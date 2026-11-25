#!/usr/bin/env python3
"""
Fake AIZA tag on a Linux laptop (BlueZ), for testing the app's real Bluetooth layer before the
firmware exists. Implements aiza_firmware/BLE_PROTOCOL.md: advertising, bonding, Immediate Alert,
Link Loss, Battery, Device Information and the AIZA service.

Run:  python3 fake_tag.py
Keys: Enter = short button press · p = pairing mode (60 s) · b <0-100> = battery · q = quit

Not the real thing: the laptop's address does not rotate and its signal strength says nothing about the
real tag's, so do not use it for wedge tuning.
"""
import sys
import time

import dbus
import dbus.exceptions
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

BLUEZ = "org.bluez"
OM_IFACE = "org.freedesktop.DBus.ObjectManager"
PROP_IFACE = "org.freedesktop.DBus.Properties"
GATT_SERVICE_IFACE = "org.bluez.GattService1"
GATT_CHRC_IFACE = "org.bluez.GattCharacteristic1"
ADV_IFACE = "org.bluez.LEAdvertisement1"
AGENT_IFACE = "org.bluez.Agent1"

AIZA = "9a99{:04x}-982c-45e1-a09e-5e8952971cdb"
AIZA_SERVICE = AIZA.format(0)
FIND_MODE_CONTROL = AIZA.format(1)
BUTTON_EVENT = AIZA.format(2)
SETTINGS = AIZA.format(3)
AIZA_PAIRING = AIZA.format(0x0100)

NORMAL_INTERVAL_MS = 1000
PAIRING_INTERVAL_MS = 100


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


class InvalidArgs(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.InvalidArguments"


class NotSupported(dbus.exceptions.DBusException):
    _dbus_error_name = "org.bluez.Error.NotSupported"


# ---------------------------------------------------------------- GATT plumbing (BlueZ example style)

class Application(dbus.service.Object):
    def __init__(self, bus):
        self.path = "/com/aiza/faketag"
        self.services = []
        dbus.service.Object.__init__(self, bus, self.path)

    def add(self, service):
        self.services.append(service)

    @dbus.service.method(OM_IFACE, out_signature="a{oa{sa{sv}}}")
    def GetManagedObjects(self):
        out = {}
        for s in self.services:
            out[s.get_path()] = s.get_properties()
            for c in s.characteristics:
                out[c.get_path()] = c.get_properties()
        return out


class Service(dbus.service.Object):
    def __init__(self, bus, index, uuid):
        self.path = f"/com/aiza/faketag/service{index}"
        self.uuid = uuid
        self.characteristics = []
        self.bus = bus
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {GATT_SERVICE_IFACE: {
            "UUID": self.uuid, "Primary": True,
            "Characteristics": dbus.Array([c.get_path() for c in self.characteristics], signature="o"),
        }}

    def chrc(self, cls, *args):
        c = cls(self.bus, len(self.characteristics), self, *args)
        self.characteristics.append(c)
        return c


class Characteristic(dbus.service.Object):
    def __init__(self, bus, index, service, uuid, flags):
        self.path = f"{service.path}/char{index}"
        self.uuid = uuid
        self.flags = flags
        self.service = service
        self.notifying = False
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    def get_properties(self):
        return {GATT_CHRC_IFACE: {"Service": self.service.get_path(), "UUID": self.uuid, "Flags": self.flags}}

    @dbus.service.method(PROP_IFACE, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        return self.get_properties()[GATT_CHRC_IFACE]

    @dbus.service.method(GATT_CHRC_IFACE, in_signature="a{sv}", out_signature="ay")
    def ReadValue(self, options):
        return dbus.Array([dbus.Byte(b) for b in self.read()], signature="y")

    @dbus.service.method(GATT_CHRC_IFACE, in_signature="aya{sv}")
    def WriteValue(self, value, options):
        self.write(bytes(value))

    @dbus.service.method(GATT_CHRC_IFACE)
    def StartNotify(self):
        self.notifying = True

    @dbus.service.method(GATT_CHRC_IFACE)
    def StopNotify(self):
        self.notifying = False

    @dbus.service.signal(PROP_IFACE, signature="sa{sv}as")
    def PropertiesChanged(self, interface, changed, invalidated):
        pass

    def notify(self, data):
        if self.notifying:
            self.PropertiesChanged(GATT_CHRC_IFACE, {"Value": dbus.Array([dbus.Byte(b) for b in data], signature="y")}, [])

    def read(self):
        raise NotSupported()

    def write(self, value):
        raise NotSupported()


# ---------------------------------------------------------------- AIZA characteristics

class Tag:
    """State of the pretend tag."""
    def __init__(self):
        self.battery = 86
        self.link_loss_level = 0
        self.find_until = 0.0
        self.button_counter = 0
        self.settings = bytes([50, 0, 0x2C, 0x01, 0])  # 50 ms, 300 s, 0 dBm
        self.ring_timer = None
        self.find_timer = None
        self.on_find_change = lambda on: None


class AlertLevel(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, "00002a06-0000-1000-8000-00805f9b34fb", ["write-without-response", "encrypt-write"])
        self.tag = tag

    def write(self, value):
        if len(value) != 1:
            raise InvalidArgs()
        if self.tag.ring_timer:
            GLib.source_remove(self.tag.ring_timer)
            self.tag.ring_timer = None
        if value[0] == 0:
            log("ring: stopped")
            return
        log(f"ring: RINGING (level {value[0]}) \a")
        def stop():
            log("ring: stopped by itself after 30 s")
            self.tag.ring_timer = None
            return False
        self.tag.ring_timer = GLib.timeout_add_seconds(30, stop)


class LinkLossLevel(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, "00002a06-0000-1000-8000-00805f9b34fb", ["read", "write", "encrypt-write"])
        self.tag = tag

    def read(self):
        return bytes([self.tag.link_loss_level])

    def write(self, value):
        self.tag.link_loss_level = value[0]
        log(f"link loss level set to {value[0]}")


class BatteryLevel(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, "00002a19-0000-1000-8000-00805f9b34fb", ["read", "notify"])
        self.tag = tag

    def read(self):
        return bytes([self.tag.battery])


class StringChar(Characteristic):
    def __init__(self, bus, i, svc, uuid, text):
        super().__init__(bus, i, svc, uuid, ["read"])
        self.text = text

    def read(self):
        return self.text.encode()


class FindModeControl(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, FIND_MODE_CONTROL, ["write", "encrypt-write"])
        self.tag = tag

    def write(self, value):
        if len(value) != 3:
            raise InvalidArgs()
        on, timeout = value[0] == 1, value[1] | (value[2] << 8)
        if self.tag.find_timer:
            GLib.source_remove(self.tag.find_timer)
            self.tag.find_timer = None
        if on:
            log(f"find mode: ON for {timeout} s")
            def end():
                log("find mode: ended by itself (time limit)")
                self.tag.find_timer = None
                self.tag.on_find_change(False)
                return False
            self.tag.find_timer = GLib.timeout_add_seconds(max(timeout, 1), end)
        else:
            log("find mode: off")
        self.tag.on_find_change(on)


class ButtonEventChar(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, BUTTON_EVENT, ["notify"])
        self.tag = tag

    def press(self):
        self.tag.button_counter = (self.tag.button_counter + 1) % 256
        if not self.notifying:
            log("button: pressed, but no phone is subscribed")
        else:
            log(f"button: short press sent (#{self.tag.button_counter})")
        self.notify(bytes([1, self.tag.button_counter]))


class SettingsChar(Characteristic):
    def __init__(self, bus, i, svc, tag):
        super().__init__(bus, i, svc, SETTINGS, ["read", "write", "encrypt-read", "encrypt-write"])
        self.tag = tag

    def read(self):
        return self.tag.settings

    def write(self, value):
        if len(value) != 5:
            raise InvalidArgs()
        self.tag.settings = bytes(value)
        log(f"settings: interval {value[0] | value[1] << 8} ms, max {value[2] | value[3] << 8} s, tx {int.from_bytes(value[4:5], 'little', signed=True)} dBm")


# ---------------------------------------------------------------- advertising and pairing

class Advertisement(dbus.service.Object):
    def __init__(self, bus, index, interval_ms, pairing):
        self.path = f"/com/aiza/faketag/adv{index}"
        self.interval_ms = interval_ms
        self.pairing = pairing
        dbus.service.Object.__init__(self, bus, self.path)

    def get_path(self):
        return dbus.ObjectPath(self.path)

    @dbus.service.method(PROP_IFACE, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        props = {
            "Type": "peripheral",
            "ServiceUUIDs": dbus.Array([AIZA_PAIRING if self.pairing else AIZA_SERVICE], signature="s"),
            "MinInterval": dbus.UInt32(self.interval_ms),
            "MaxInterval": dbus.UInt32(self.interval_ms),
            "Discoverable": dbus.Boolean(self.pairing),
        }
        return props

    @dbus.service.method(ADV_IFACE)
    def Release(self):
        pass


class Agent(dbus.service.Object):
    """No display, no keyboard: LE Secure Connections Just Works, accepted only in pairing mode."""
    def __init__(self, bus, path, is_pairing):
        self.is_pairing = is_pairing
        dbus.service.Object.__init__(self, bus, path)

    def _check(self):
        if not self.is_pairing():
            log("pairing: refused (not in pairing mode; press p)")
            raise dbus.exceptions.DBusException("Not in pairing mode", name="org.bluez.Error.Rejected")

    @dbus.service.method(AGENT_IFACE, in_signature="o", out_signature="")
    def RequestAuthorization(self, device):
        self._check()
        log("pairing: accepted")

    @dbus.service.method(AGENT_IFACE, in_signature="ou", out_signature="")
    def RequestConfirmation(self, device, passkey):
        self._check()
        log("pairing: accepted (Just Works)")

    @dbus.service.method(AGENT_IFACE, in_signature="os", out_signature="")
    def AuthorizeService(self, device, uuid):
        pass

    @dbus.service.method(AGENT_IFACE, in_signature="", out_signature="")
    def Release(self):
        pass

    @dbus.service.method(AGENT_IFACE, in_signature="", out_signature="")
    def Cancel(self):
        pass


class FakeTag:
    def __init__(self):
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        self.bus = dbus.SystemBus()
        self.adapter_path = self.find_adapter()
        self.adapter = dbus.Interface(self.bus.get_object(BLUEZ, self.adapter_path), PROP_IFACE)
        self.adv_manager = dbus.Interface(self.bus.get_object(BLUEZ, self.adapter_path), "org.bluez.LEAdvertisingManager1")
        self.tag = Tag()
        self.tag.on_find_change = self.set_find
        self.pairing_until = 0.0
        self.find = False
        self.adv = None
        self.adv_index = 0

        app = Application(self.bus)
        ias = Service(self.bus, 0, "00001802-0000-1000-8000-00805f9b34fb"); ias.chrc(AlertLevel, self.tag); app.add(ias)
        lls = Service(self.bus, 1, "00001803-0000-1000-8000-00805f9b34fb"); lls.chrc(LinkLossLevel, self.tag); app.add(lls)
        bas = Service(self.bus, 2, "0000180f-0000-1000-8000-00805f9b34fb"); self.battery = bas.chrc(BatteryLevel, self.tag); app.add(bas)
        dis = Service(self.bus, 3, "0000180a-0000-1000-8000-00805f9b34fb")
        dis.chrc(StringChar, "00002a24-0000-1000-8000-00805f9b34fb", "AIZA-laptop")
        dis.chrc(StringChar, "00002a25-0000-1000-8000-00805f9b34fb", "AZ-LAPT-0001")
        dis.chrc(StringChar, "00002a26-0000-1000-8000-00805f9b34fb", "fake-0.1")
        app.add(dis)
        aiza = Service(self.bus, 4, AIZA_SERVICE)
        aiza.chrc(FindModeControl, self.tag)
        self.button = aiza.chrc(ButtonEventChar, self.tag)
        aiza.chrc(SettingsChar, self.tag)
        app.add(aiza)

        self.adapter.Set("org.bluez.Adapter1", "Powered", dbus.Boolean(True))
        gatt = dbus.Interface(self.bus.get_object(BLUEZ, self.adapter_path), "org.bluez.GattManager1")
        gatt.RegisterApplication(dbus.ObjectPath(app.path), {},
                                 reply_handler=lambda: log("GATT services registered"),
                                 error_handler=lambda e: self.fail(f"GATT registration failed: {e}"))
        agent_path = "/com/aiza/faketag/agent"
        Agent(self.bus, agent_path, lambda: time.time() < self.pairing_until)
        agents = dbus.Interface(self.bus.get_object(BLUEZ, "/org/bluez"), "org.bluez.AgentManager1")
        agents.RegisterAgent(agent_path, "NoInputNoOutput")
        agents.RequestDefaultAgent(agent_path)
        self.bus.add_signal_receiver(self.on_props, dbus_interface=PROP_IFACE, signal_name="PropertiesChanged",
                                     arg0="org.bluez.Device1", path_keyword="path")
        self.advertise()

    def find_adapter(self):
        objects = dbus.Interface(self.bus.get_object(BLUEZ, "/"), OM_IFACE).GetManagedObjects()
        for path, ifaces in objects.items():
            if "org.bluez.LEAdvertisingManager1" in ifaces and "org.bluez.GattManager1" in ifaces:
                return path
        self.fail("No Bluetooth adapter with LE advertising and GATT server support")

    def fail(self, msg):
        print("ERROR:", msg, file=sys.stderr)
        sys.exit(1)

    def pairing(self):
        return time.time() < self.pairing_until

    def advertise(self):
        if self.adv is not None:
            try:
                self.adv_manager.UnregisterAdvertisement(self.adv.get_path())
            except dbus.exceptions.DBusException:
                pass
            self.adv.remove_from_connection()
        self.adv_index += 1
        interval = PAIRING_INTERVAL_MS if self.pairing() else (self.tag.settings[0] | self.tag.settings[1] << 8) if self.find else NORMAL_INTERVAL_MS
        self.adv = Advertisement(self.bus, self.adv_index, interval, self.pairing())
        mode = "pairing" if self.pairing() else "find" if self.find else "normal"
        self.adv_manager.RegisterAdvertisement(self.adv.get_path(), {},
                                               reply_handler=lambda: log(f"advertising: {mode}, every {interval} ms"),
                                               error_handler=lambda e: log(f"advertising failed: {e}"))

    def set_find(self, on):
        self.find = on
        self.advertise()

    def start_pairing(self):
        self.pairing_until = time.time() + 60
        self.adapter.Set("org.bluez.Adapter1", "Pairable", dbus.Boolean(True))
        log("pairing mode: ON for 60 s (beep)")
        self.advertise()
        def end():
            if not self.pairing():
                self.adapter.Set("org.bluez.Adapter1", "Pairable", dbus.Boolean(False))
                log("pairing mode: off")
                self.advertise()
            return False
        GLib.timeout_add_seconds(61, end)

    def on_props(self, interface, changed, invalidated, path=None):
        if "Connected" in changed:
            log(f"{'connected' if changed['Connected'] else 'disconnected'}: {path.split('/')[-1]}")
            if not changed["Connected"]:
                if self.tag.link_loss_level:
                    log(f"link lost: tag-side alert level {self.tag.link_loss_level} \a")
                self.advertise()  # keep advertising after a disconnect
        if changed.get("Paired"):
            log(f"bonded with {path.split('/')[-1]}")

    def on_stdin(self, source, condition):
        line = sys.stdin.readline()
        if not line:
            loop.quit()
            return False
        cmd = line.strip().split()
        if not cmd:
            self.button.press()
        elif cmd[0] == "p":
            self.start_pairing()
        elif cmd[0] == "b" and len(cmd) == 2 and cmd[1].isdigit():
            self.tag.battery = max(0, min(100, int(cmd[1])))
            self.battery.notify(bytes([self.tag.battery]))
            log(f"battery: {self.tag.battery}%")
        elif cmd[0] == "q":
            loop.quit()
            return False
        else:
            print("Enter = button press · p = pairing mode · b <0-100> = battery · q = quit")
        return True


if __name__ == "__main__":
    tag = FakeTag()
    print("Fake AIZA tag running. Enter = button press · p = pairing mode (60 s) · b <0-100> = battery · q = quit")
    loop = GLib.MainLoop()
    GLib.io_add_watch(sys.stdin, GLib.IO_IN, tag.on_stdin)
    try:
        loop.run()
    except KeyboardInterrupt:
        pass

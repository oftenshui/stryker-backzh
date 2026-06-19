#!/bin/sh

function error {
    echo "Stryker VNC setup helper <<"
    echo ""
    echo "Failed to update packages, check access to repository or your internet connection."
    exit 1
}

function scripts_writing_error() {
    echo "Stryker VNC setup helper <<"
    echo ""
    echo "Failed to write scripts. Critical error!"
    exit 1
}

apk update || error
# xfce4-session + panel + desktop + wm + xfconf are the actual moving parts
# behind `startxfce4`; the bare `xfce4` metapackage is sometimes incomplete in
# Alpine. mesa-dri-gallium gives a software GL driver so xfwm4's compositor
# can render inside a chroot without GPU access (otherwise xfwm dies and you
# get a bare X with default cursor). ttf-dejavu prevents the "no fonts" blank.
apk add --no-cache ca-certificates curl openssl xvfb x11vnc xfce4 xfce4-session \
    xfce4-panel xfdesktop xfwm4 xfconf xfce4-terminal faenza-icon-theme \
    dbus dbus-x11 ttf-dejavu mesa-dri-gallium mesa-gl xset xrdb \
    xinit setxkbmap || error

PASS=stryker

[ ! -f /root/.vnc/passwd ] && echo "No previous VNC password found. Setting $PASS as default password!" && mkdir -p /root/.vnc && x11vnc -storepasswd $PASS /root/.vnc/passwd || echo "Previously generated password found. Keeping your old password!"

# /etc/machine-id is mandatory for dbus-daemon to start. dbus-uuidgen creates
# one if missing; we copy it to /var/lib/dbus/machine-id too for older code.
[ ! -s /etc/machine-id ] && dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null
mkdir -p /var/lib/dbus
[ ! -s /var/lib/dbus/machine-id ] && cp /etc/machine-id /var/lib/dbus/machine-id

echo "#!/bin/bash

function usage () {
    echo \"Stryker VNC setup helper <<\"
    echo \"\"
    echo \"Usage: vncserver-start\"
    echo \"-p|--port Setup port for VNC\"
    echo \"-r|--resolution Setup resolution for VNC\"
    exit 1
}

while [[ \$# -gt 0 ]]; do
    case \$1 in
        -p|--port)
            PORT=\"\$2\"
            shift
            shift
        ;;
        -r|--resolution)
            RESOLUTION=\"\$2\"
            shift
            shift
        ;;
        --pulse)
            PULSE_PORT=\"\$2\"
            shift
            shift
        ;;
        *) break
    esac
done

if [ -z \$PORT ]; then
    usage
fi
if [ -z \$RESOLUTION ]; then
    usage
fi

if [ -n \$PULSE_PORT ]; then
    export PULSE_SERVER=tcp:0.0.0.0:\$PULSE_PORT
fi

# IMPORTANT: env leaks through chroot_exec from the parent Android process.
# Without this, HOME points at /data/user/0/com.zalexdev.stryker and xfce4
# tries to write its session files into a path that doesn't exist inside
# the chroot — symptom is a dark screen with the default X cursor.
unset DBUS_SESSION_BUS_ADDRESS XAUTHORITY ICEAUTHORITY SESSION_MANAGER
unset ANDROID_ROOT ANDROID_DATA ANDROID_STORAGE EXTERNAL_STORAGE
unset GTK_PATH GIO_LAUNCHED_DESKTOP_FILE_PID GIO_LAUNCHED_DESKTOP_FILE
unset DESKTOP_SESSION GNOME_KEYRING_CONTROL TMPDIR TEMP TMP

export DISPLAY=:1
export HOME=/root
export USER=root
export LOGNAME=root
export XDG_RUNTIME_DIR=/tmp/runtime-root
export XDG_CONFIG_HOME=/root/.config
export XDG_CACHE_HOME=/root/.cache
export XDG_DATA_HOME=/root/.local/share
export XDG_CONFIG_DIRS=/etc/xdg
export XDG_DATA_DIRS=/usr/local/share:/usr/share
export XDG_SESSION_TYPE=x11
export LANG=C.UTF-8
export LC_ALL=C.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# No GPU in the chroot — force the mesa software rasteriser. Without this
# xfwm4's compositor dies and you get a bare X with the default cursor.
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe

mkdir -p \$XDG_RUNTIME_DIR && chmod 700 \$XDG_RUNTIME_DIR
mkdir -p \$XDG_CONFIG_HOME \$XDG_CACHE_HOME \$XDG_DATA_HOME

# Tear down any zombie state from a previous broken session so the new Xvfb
# can actually grab :1 and we don't end up with two compositors fighting.
rm -rf /tmp/.X1-lock /tmp/.X11-unix/X1 /tmp/dbus-* 2>/dev/null
pkill -9 -f 'Xvfb :1' 2>/dev/null
pkill -9 -f 'xfce4-session' 2>/dev/null
pkill -9 -f 'x11vnc.*-rfbport' 2>/dev/null
pkill -9 xfwm4 xfdesktop xfce4-panel xfconfd xfsettingsd 2>/dev/null
sleep 1

# Boot the system D-Bus daemon so xfconfd can register. xfconfd is what XFCE
# uses to read all settings; without it the panel/desktop won't start.
mkdir -p /var/run/dbus
[ ! -s /etc/machine-id ] && dbus-uuidgen --ensure=/etc/machine-id 2>/dev/null
[ -z \"\$(pidof dbus-daemon)\" ] && dbus-daemon --system --fork

# Reset stale xfconfd cache (corrupted configs leave xfdesktop frozen).
rm -rf /root/.cache/sessions /root/.cache/xfce4 2>/dev/null

/usr/bin/Xvfb \$DISPLAY -screen 0 \$RESOLUTION -ac +extension GLX +render -noreset 2>/tmp/xvfb.log &
sleep 2

# Disable screen blanking — DPMS triggering inside Xvfb turns the display
# off and looks identical to a dead session.
xset -display \$DISPLAY s off s noblank 2>/dev/null
xset -display \$DISPLAY -dpms 2>/dev/null

# dbus-run-session creates a fresh session bus, runs xfce4-session inside it,
# and tears the bus down on exit. This is the supported way since Alpine 3.18.
# Calling xfce4-session directly skips startxfce4's shell-script wrapper that
# was forking and detaching, leaving the session bus dangling.
dbus-run-session -- xfce4-session >/tmp/xfce.log 2>&1 &
sleep 5

x11vnc -xkb -noxrecord -noxfixes -noxdamage -display \$DISPLAY -forever -bg \
    -rfbauth /root/.vnc/passwd -users root -rfbport \$PORT -noshm 2>/tmp/x11vnc.log
echo \"[!] VNC server started at: localhost:\$PORT\"
echo \"[!] If you see a dark screen with an X cursor: cat /tmp/xfce.log\"" > /usr/local/bin/vncserver-start || scripts_writing_error

echo "#!/bin/bash
pkill -9 -f x11vnc
pkill -9 -f Xvfb
pkill pulse
for i in \$(pidof startxfce4; pidof xfce4-session; pidof xfconfd; pidof xfce4-panel; pidof xfce4-power-manager; pidof xfdesktop; pidof xfwm4); do
    kill \$i;
done
pkill -9 dbus-daemon 2>/dev/null
rm -rf /tmp/.X1-lock /tmp/.X11-unix/X1 2>/dev/null" > /usr/local/bin/vncserver-stop || scripts_writing_error

echo "#!/bin/bash
read -p \"Provide a new VNC password: \" PASSWORD
mkdir -p /root/.vnc && x11vnc -storepasswd \$PASSWORD /root/.vnc/passwd" > /usr/local/bin/vncpasswd || scripts_writing_error

echo "#!/bin/bash
PASSWORD=\$1
if [ -z \$PASSWORD ]; then
    echo \"Password isn't defined, exiting...\"
    exit 1
fi
mkdir -p /root/.vnc && x11vnc -storepasswd \"\$PASSWORD\" /root/.vnc/passwd" > /usr/local/bin/vncpasswd-setup || scripts_writing_error

# Tail of recent logs — copy/paste this to Stryker if VNC misbehaves
echo "#!/bin/bash
echo '===== xvfb.log ====='; tail -40 /tmp/xvfb.log 2>/dev/null
echo '===== xfce.log ====='; tail -80 /tmp/xfce.log 2>/dev/null
echo '===== x11vnc.log ====='; tail -40 /tmp/x11vnc.log 2>/dev/null
echo '===== ps ====='; ps aux | grep -E 'Xvfb|xfce|x11vnc|dbus|xfwm' | grep -v grep" > /usr/local/bin/vnc-diag || scripts_writing_error

chmod +x /usr/local/bin/vncserver-st* || scripts_writing_error
chmod +x /usr/local/bin/vncpasswd* || scripts_writing_error
chmod +x /usr/local/bin/vnc-diag || scripts_writing_error
echo "[!] Use the helper scripts vncserver-start and vncserver-stop to start and stop Stryker XFCE."
echo "[!] Run vnc-diag if you get a dark screen — it dumps all VNC logs."

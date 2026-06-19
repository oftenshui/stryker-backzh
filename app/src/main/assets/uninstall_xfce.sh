#!/bin/sh

function deleting_packages_error() {
    echo "Error while removing VNC packages, please try removing them manually."
    exit 1
}

function deleting_vnc_files_error() {
    echo "Failed to delete some VNC files, please try to delete them manually."
    exit 1
}

apk del openssl xvfb x11vnc xfce4 xfce4-terminal faenza-icon-theme dbus-x11 || deleting_packages_error
rm -rf /root/Desktop /root/Documents /root/Downloads /root/Music /root/Pictures /root/Public /root/Templates /root/Videos /root/.cache /root/.config /root/.local /root/.vnc /tmp/.X1-lock || deleting_vnc_files_error
cat >/home/${OWNER}/Desktop/VMD.desktop <<EOL
[Desktop Entry]
Type=Application
Icon=/opt/vmd/vmd.png
Name=VMD
Terminal=true
Exec=vmd
Hidden=false
EOL

chmod +x /home/${OWNER}/Desktop/VMD.desktop
chown ${OWNER} /home/${OWNER}/Desktop/VMD.desktop

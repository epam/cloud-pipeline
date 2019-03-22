cat >/home/${OWNER}/Desktop/NEdit.desktop <<EOL
[Desktop Entry]
Type=Application
Icon=/etc/nedit.png
Name=NEdit
Terminal=false
Exec=nedit
Hidden=false
EOL

# Fix permissions
chmod +x /home/${OWNER}/Desktop/*.desktop 
chown ${OWNER} /home/${OWNER}/Desktop/*.desktop 

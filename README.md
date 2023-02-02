# This mod helps player set up and target [m³'s cannon](https://youtu.be/187oOzMrqRM).

## Setup
- Launch Minecraft with mod installed.
- Open `.minecraft/config/ucsm`.
- Put `Pt.txt` and `Ct.txt` in that folder.

## Usage
- `/ucsm reload`: reload config files.
- `/ucsm precision <int>`: maximum distance to the explosion.
- `/ucsm origin [<pos> <direction>] [mirrored]`: set cannon origin to a location, uses player position and facing direction if no arguments provided.
- `/ucsm target [<pos>]`: output closest configuration to specified position. Uses block player is looking at (even very far) if no argument is provided.
- `/ucsm pack`: pack Ct.txt and Pt.txt into a binary format to reduce file size.
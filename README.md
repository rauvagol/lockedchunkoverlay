# Locked Chunk Overlay

Shades forbidden regions and optionally warns when you enter them.

Recommended that you use the Region Locker plugin to know which regions are unlocked: [Region Locker](https://github.com/slaytostay/region-locker) But you can also input them manually in the configuration.

Known limitation (why overlays sometimes sit “on top” of objects):
The chunk tint is drawn as an overlay after the 3D scene, so it sits "between" things like buildings and the screen. 
The only way around this would be to draw the tint inside the game’s graphics engine so it behaves like an actual overlay on the real ground tiles. 
Since this plugin was designed to work with the [117 HD](https://github.com/117HD/RLHD) GPU plugin (or any other GPU plugin of your choice) that is not an option.
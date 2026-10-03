-- Current Omarchy uses Lua overrides. Add once to ~/.config/hypr/autostart.lua.
o.launch_on_start("librepods --hide")

-- Optional examples for ~/.config/hypr/bindings.lua. Choose unused combinations.
o.bind("SUPER + CTRL + ALT + A", "LibrePods", "librepods")
o.bind("SUPER + CTRL + ALT + N", "AirPods ANC", "librepods-ctl noise:anc")
o.bind("SUPER + CTRL + ALT + T", "AirPods transparency", "librepods-ctl noise:transparency")

# Custom LibrePods update: v1.0.1-rc1

Integrated the upstream release tag `v1.0.1-rc1`, commit
`efccc4499b2c1fd2ac243e975ae1f42d8e828ffd`, into the custom fork.

Upstream adds AirPods 5 model definitions and prevents unwanted automatic
connections on service start. This fork also registers both new model classes
for actual model-number lookup, with regression coverage for the new models,
existing Pro 3 models, unknown numbers and duplicate entries.

Android version code is 66, version name `1.0.1-rc1-debug`, using the existing
signing certificate for an in-place update. Free feature access, widgets,
Quick Settings controls, notification suppression, workout/Health Connect
integration, experimental audio tools and the existing settings are retained.
AirPods case UWB precision finding remains unavailable; the lab tools do not
establish a supported case finding feature.

Omarchy package version is `1.0.1rc1-1`. Upstream's Qt Linux sources did not
change in this release; the rebuilt package retains our BlueZ controls, CLI,
tray, optional Waybar module and Hyprland integration. No boot-chain or Secure
Boot changes are required by the application package. Installation and real
AirPods behavior on the user's Omarchy PC remain separate acceptance checks.

Build and delivery evidence will be recorded after verification.

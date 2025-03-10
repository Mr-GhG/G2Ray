# G2Ray(V2Ray)
This quick V2Ray mod will help you get your VPN up and running. This version will work with Kotlin Jetpack Compose.

## Implementation
Download the module.

In Android Studio File -> New -> Import Module

```
implementation(project(":g2ray"))
```

## Initialization
```
V2rayController.init(this, R.drawable.icon, "VPN")
```

## Start connection
```
V2rayController.startV2ray(this, "Test Server", "vless://...", null)
```

## Stop connection
```
V2rayController.stopV2ray(this)
```

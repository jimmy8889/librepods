<#
Read-only backend report. Does not install drivers, transfer USB devices, pair
AirPods, start WSL distributions, or change boot/firmware/audio settings.
Run in Windows PowerShell; elevation is optional but may be needed to read
Secure Boot. Copy the JSON report when choosing a backend.
#>
$ErrorActionPreference = 'Stop'

$secureBoot = 'Unknown'
try {
    if (Confirm-SecureBootUEFI -ErrorAction Stop) { $secureBoot = 'Enabled' }
    else { $secureBoot = 'Disabled' }
} catch { }

$testSigning = 'Unknown'
try {
    $options = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control' -Name SystemStartOptions -ErrorAction Stop).SystemStartOptions
    if ($options -match '(?i)\bTESTSIGNING\b') { $testSigning = 'Enabled' }
    else { $testSigning = 'Not reported in active boot options' }
} catch { }

$usbRadios = @()
$radioQuery = 'Available'
try {
    foreach ($radio in @(Get-PnpDevice -Class Bluetooth -PresentOnly -ErrorAction Stop)) {
        # Filter Windows' Bluetooth stack radios, not paired earbuds/enumerators.
        # Print VID/PID only; never emit device instance paths or USB serials.
        if ($radio.InstanceId -match '^USB\\VID_([0-9A-F]{4})&PID_([0-9A-F]{4})') {
            $vid = $Matches[1]; $pid = $Matches[2]
            $usbRadios += [ordered]@{
                name = $radio.FriendlyName
                status = [string]$radio.Status
                vendorId = $vid
                productId = $pid
                dedicatedExternalAdapter = 'Unknown; internal radios can also use USB'
            }
        }
    }
} catch { $radioQuery = 'Unavailable; device-query access or PnpDevice module missing' }

$wsl = Get-Command wsl.exe -ErrorAction SilentlyContinue
$usbipd = Get-Command usbipd.exe -ErrorAction SilentlyContinue
$os = Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
$networkAdapters = @()
try {
    # Descriptions only: omit MAC addresses, GUIDs, IPs and network names.
    $networkAdapters = @(Get-NetAdapter -Physical -ErrorAction Stop | ForEach-Object { [string]$_.InterfaceDescription })
} catch { }

[ordered]@{
    schema = 1
    secureBoot = $secureBoot
    testSigning = $testSigning
    windowsBuild = [string]$os.CurrentBuildNumber
    wslCommandInstalled = [bool]$wsl
    usbipdCommandInstalled = [bool]$usbipd
    usbBluetoothRadioQuery = $radioQuery
    usbBluetoothRadios = @($usbRadios)
    physicalNetworkAdapterDescriptions = @($networkAdapters)
    requirement = 'Built-in Bluetooth only; keep Secure Boot enabled'
    notes = @(
        'WSL availability here means the command exists, not that WSL 2 or Bluetooth is configured.'
        'An adapter passed through to WSL is unavailable to Windows until detached.'
        'USB radios listed here are candidates only; transport, firmware and audio coexistence still need testing.'
        'An internal USB Bluetooth function is still built-in hardware; Wi-Fi on the same module can have a separate bus interface.'
        'A separate USB adapter has been ruled out by the owner.'
        'The current NTPods kernel drivers still require Microsoft signing for Secure Boot.'
    )
} | ConvertTo-Json -Depth 6

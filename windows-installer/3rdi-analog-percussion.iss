#define MyAppName "3RDI Analog Percussion"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "3RDI Audio Labs"
#define MyAppExeName "3RDI Analog Percussion.exe"

[Setup]
AppId={{D79F9E31-88D1-4D82-A7C9-3RDI00000001}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\3RDI Audio Labs\3RDI Analog Percussion
DefaultGroupName=3RDI Audio Labs
DisableProgramGroupPage=yes
OutputDir={#GetEnv("OUTPUT_DIR")}
OutputBaseFilename=3RDI-Analog-Percussion-Windows-x64-Setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayName=3RDI Analog Percussion
VersionInfoCompany=3RDI Audio Labs
VersionInfoDescription=3RDI Analog Percussion VST3 and Standalone Installer
VersionInfoProductName=3RDI Analog Percussion
VersionInfoProductVersion=1.0.0

[Types]
Name: "full"; Description: "Full installation"
Name: "compact"; Description: "VST3 only"
Name: "custom"; Description: "Custom installation"; Flags: iscustom

[Components]
Name: "vst3"; Description: "VST3 Plug-In for Ableton Live and other DAWs"; Types: full compact custom; Flags: fixed
Name: "standalone"; Description: "Standalone 3RDI Analog Percussion application"; Types: full custom

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional icons:"; Components: standalone; Flags: unchecked

[Files]
Source: "{#GetEnv("VST3_SOURCE")}\*"; DestDir: "{commoncf64}\VST3\3RDI Analog Percussion.vst3"; Flags: ignoreversion recursesubdirs createallsubdirs; Components: vst3
Source: "{#GetEnv("STANDALONE_SOURCE")}"; DestDir: "{app}"; Flags: ignoreversion; Components: standalone

[Icons]
Name: "{autoprograms}\3RDI Analog Percussion"; Filename: "{app}\{#MyAppExeName}"; Components: standalone
Name: "{autodesktop}\3RDI Analog Percussion"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon; Components: standalone

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch 3RDI Analog Percussion"; Flags: nowait postinstall skipifsilent; Components: standalone

[Code]
procedure InitializeWizard;
begin
  WizardForm.WelcomeLabel1.Caption := 'Install 3RDI Analog Percussion';
  WizardForm.WelcomeLabel2.Caption :=
    'This setup installs the 64-bit VST3 plug-in for Ableton Live and can optionally install the standalone synth.' + #13#10 + #13#10 +
    'The plug-in includes the full Deep Engine, sequencer, themes, presets and the detailed hardware-style interface.';
end;

#define MyAppName "Pusula Service Manager"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Pusula"
#define MyAppExeName "frontend-desktop-1.0-SNAPSHOT.jar"
#define JreSource "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"

[Setup]
AppId={{A8B7C6D5-E4F3-2G1H-0I9J-8K7L6M5N4O3P}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=installer
OutputBaseFilename=PusulaServiceManagerSetup
SetupIconFile=src\main\resources\app.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
UninstallDisplayIcon={app}\jre\bin\javaw.exe

[Languages]
Name: "turkish"; MessagesFile: "compiler:Languages\Turkish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Application JAR
Source: "target\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
; Dependencies (including JavaFX)
Source: "target\lib\*"; DestDir: "{app}\lib"; Flags: ignoreversion recursesubdirs createallsubdirs
; Bundled JRE
Source: "{#JreSource}\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs
; Debug Script
Source: "Debug.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; IconFilename: "{app}\jre\bin\javaw.exe"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; IconFilename: "{app}\jre\bin\javaw.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\{#MyAppExeName}"""; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

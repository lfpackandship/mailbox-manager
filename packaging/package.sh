#!/usr/bin/env bash
#
# Builds an installable app for the system this runs on, with its own Java
# runtime bundled so users don't need Java installed:
#
#   macOS    dist/mailbox-manager-<version>-<platform>.dmg
#   Windows  dist/mailbox-manager-<version>-<platform>.msi   (needs the WiX Toolset)
#   Linux    dist/mailbox-manager-<version>-<platform>.AppImage
#
# The runnable jar is copied to dist/ as well. The version comes from pom.xml.
# Uses the jpackage from the JDK on the PATH (or JAVA_HOME), which is also the
# Java that gets bundled. Windows builds also need the WiX Toolset on the PATH;
# see .github/workflows/build.yml for how CI installs WiX 5.
#
# Usage: packaging/package.sh <platform>   e.g. mac-arm64, windows-x64, linux-x64

set -euo pipefail

platform=${1:?Usage: packaging/package.sh <platform>, e.g. mac-arm64}

cd "$(dirname "$0")/.."

# The project's own <version> is the first one indented by four spaces.
version=$(sed -n 's|^    <version>\(.*\)</version>.*|\1|p' pom.xml | head -1)
base="mailbox-manager-$version-$platform"

jpackage=jpackage
if [ -n "${JAVA_HOME:-}" ]; then
  jpackage="$JAVA_HOME/bin/jpackage"
fi

./mvnw -B -q clean package -DskipTests

rm -rf target/jpackage-input target/jpackage dist
mkdir -p target/jpackage-input target/jpackage dist
cp target/mailbox-manager-"$version"-shaded.jar target/jpackage-input/mailbox-manager.jar
cp target/mailbox-manager-"$version"-shaded.jar "dist/$base.jar"

common=(
  --input target/jpackage-input
  --main-jar mailbox-manager.jar
  --main-class org.lfps.mailboxes.Launcher
  --app-version "$version"
  --vendor "Mailbox Manager"
  --description "Keeps track of rented mailboxes, their holders, and renewals."
  # The Java modules the app uses (found with jdeps), so the bundled runtime
  # contains only those.
  --add-modules java.base,java.desktop,java.net.http,java.sql,jdk.httpserver,jdk.jfr,jdk.unsupported
  --dest target/jpackage
)

case "$(uname -s)" in
  Darwin)
    "$jpackage" "${common[@]}" \
      --type dmg \
      --name "Mailbox Manager" \
      --icon packaging/icons/icon.icns \
      --mac-package-identifier org.lfps.mailboxes
    mv target/jpackage/*.dmg "dist/$base.dmg"
    ;;

  MINGW* | MSYS* | CYGWIN*)
    # The upgrade UUID must never change: Windows uses it to recognize a new
    # version as an upgrade of the installed one rather than a second copy.
    # Installs for the current user only, so no administrator rights needed.
    "$jpackage" "${common[@]}" \
      --type msi \
      --name "Mailbox Manager" \
      --icon packaging/icons/icon.ico \
      --win-upgrade-uuid 1B0FDFC6-6F51-46A8-82D9-40887B3A3910 \
      --win-per-user-install \
      --win-menu \
      --win-menu-group "Mailbox Manager" \
      --win-shortcut
    mv target/jpackage/*.msi "dist/$base.msi"
    ;;

  Linux)
    # jpackage builds the app folder; appimagetool turns it into a single
    # AppImage file that runs on most distributions.
    "$jpackage" "${common[@]}" \
      --type app-image \
      --name mailbox-manager \
      --icon packaging/icons/icon.png

    appdir=target/jpackage/mailbox-manager
    cp packaging/icons/icon.png "$appdir/mailbox-manager.png"
    cat > "$appdir/mailbox-manager.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Mailbox Manager
Comment=Keeps track of rented mailboxes, their holders, and renewals
Exec=mailbox-manager
Icon=mailbox-manager
Categories=Office;
EOF
    cat > "$appdir/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/bin/mailbox-manager" "$@"
EOF
    chmod +x "$appdir/AppRun"

    appimagetool=target/appimagetool.AppImage
    curl -sSfL -o "$appimagetool" \
      https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
    chmod +x "$appimagetool"
    # Extract-and-run works without FUSE, which CI machines don't have.
    ARCH=x86_64 "$appimagetool" --appimage-extract-and-run "$appdir" "dist/$base.AppImage"
    ;;

  *)
    echo "Unsupported system: $(uname -s)" >&2
    exit 1
    ;;
esac

echo "Built:"
ls -l dist

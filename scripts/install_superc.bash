#!/bin/bash

RED='\033[0;31m'
NC='\033[0m'

install_path=$HOME

# Usage:
# Mandatory: Path to
# Optional: Path to the directory in which superc should be cloned

script_path=$(readlink -f "$0")
script_dir=$(dirname "$script_path")

# Install dependencies.
echo "Installing dependencies of SuperC..."
sudo apt-get install bison libz3-java=4.8.7-4build1 libjson-java sat4j
echo "Done installing dependencies."

# Clone SuperC.
echo "Cloning the SuperC repo..."

if [ -n "$1" ]; then
    install_path=$1
fi

if [ -d "$install_path/superc" ]; then
  echo "Removing existing SuperC directory found in install path '$install_path'."
  rm -rf "$install_path/superc"
fi

echo "Executing clone from within $install_path"
cd "$install_path" || exit
git clone https://github.com/KIT-TVA/superc.git
echo "Done cloning SuperC."

cd superc || exit

# Set environment variables.
echo "Setting up environment variables..."

echo "Backing up .bashrc to .bashrc.bak."
cp ~/.bashrc ~/.bashrc.bak

# Add variables to .bashrc.
echo "# SuperC environment variables set by Vari-Joern's install_superc script." >> ~/.bashrc

# JAVA_DEV_ROOT.
if ! grep -q "^export.*JAVA_DEV_ROOT" ~/.bashrc; then
  echo "export JAVA_DEV_ROOT=$(pwd)" >> ~/.bashrc
else
  echo -e "${RED} Warning: JAVA_DEV_ROOT was not written to .bashrc since it appears to be already defined.${NC}"
fi

# CLASSPATH.
echo 'CLASSPATH=$CLASSPATH:'\
'$JAVA_DEV_ROOT/classes:'\
'$JAVA_DEV_ROOT/bin/junit.jar:'\
'$JAVA_DEV_ROOT/bin/antlr.jar:'\
'$JAVA_DEV_ROOT/bin/javabdd.jar:'\
'$JAVA_DEV_ROOT/bin/json-simple-1.1.1.jar' >> ~/.bashrc
echo 'CLASSPATH=$CLASSPATH:'\
'/usr/share/java/org.sat4j.core.jar:'\
'/usr/share/java/com.microsoft.z3.jar:'\
'/usr/share/java/json-lib.jar' >> ~/.bashrc
echo "export CLASSPATH" >> ~/.bashrc

echo "Done setting up environment variables."

# Build SuperC.
echo "Building SuperC from within '$(pwd)'"

# TODO Debug why make calls do not work.

if ! make configure; then
  echo -e "${RED}Hint: make configure failed.${NC}"
  exit 1
fi

if ! make; then
  echo -e "${RED}Hint: make failed.${NC}"
    exit 1
fi

echo "Done building SuperC."

# Copy jars into lib directory,
echo "Copying necessary jars from SuperC to lib directory..."
cp "$install_path/superc/bin/xtc.jar" "$install_path/superc/bin/superc.jar"  "$script_dir/../lib/"
echo "Done copying necessary jars."

echo -e "${RED}Hint: Please run 'source ~/.bashrc' or restart open terminals to apply changes to the environment variables.${NC}"
echo -e "${RED}Hint: Also make sure that JAVA_HOME points to a JDK >= Java 8 (currently points to '$JAVA_HOME').${NC}"
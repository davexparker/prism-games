This directory contains pre-compiled versions of the Parma Polyhedra Library (PPL) library
and its Java interface. The Makefile copies the correct versions to the PRISM lib directory.

---

Instructions to build the libraries from source on different OSs are below.

We build GMP from source first. This allows us to statically link GMP
to the PPL shared libraries, minimising dependencies to bundle.
Also, we need JNI friendly libraries for Cygwin.
We use a custom version of PPL that has some build fixes and additions,
including the option to statically link GMP. 

---

### Linux

(assuming set up as in prism-install-ubuntu)

```
# Pre-requisites
sudo apt -y install autoconf automake libtool

# Set-up
export BUILD_DIR="$HOME/tools"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

# Install a dynamic GMP and build a static GMP (incl. C++)
sudo apt -y install libgmp-dev
mkdir -p $BUILD_DIR && cd $BUILD_DIR && mkdir -p dynamic_gmp && mkdir -p static_gmp
wget https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz
tar xf gmp-6.3.0.tar.xz 
cd gmp-6.3.0
./configure --enable-cxx --enable-static --disable-shared --prefix=$BUILD_DIR/static_gmp CC=gcc ABI=64 CFLAGS='-fPIC -m64' CPPFLAGS=-DPIC
make && make install
# make check

# Build (custom) PPL with Java interface
mkdir -p $BUILD_DIR && cd $BUILD_DIR
git clone https://github.com/prismmodelchecker/ppl.git
cd ppl
autoreconf -i
./configure --enable-interfaces=java --with-java="$JAVA_HOME" --disable-documentation --prefix=$BUILD_DIR --with-gmp-include="$BUILD_DIR/static_gmp/include" --with-gmp-lib-static="$BUILD_DIR/static_gmp/lib/libgmpxx.a $BUILD_DIR/static_gmp/lib/libgmp.a" CXXFLAGS="-std=c++11" JAVACFLAGS="--release 9"
make
make install
cp $BUILD_DIR/lib/libppl.so.15 $BUILD_DIR/lib/ppl/libppl_java.so $BUILD_DIR/lib/ppl/ppl_java.jar $BUILD_DIR
```

Files `libppl.so.15`, `libppl_java.so` an `ppl_java.jar` are then in `$BUILD_DIR`.

### macOS

```
# Pre-requisites
brew install autoconf automake libtool

# Set-up
export BUILD_DIR="$HOME/tools/tmp"
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/20.0.2/libexec/openjdk.jdk/Contents/Home

# Install a dynamic GMP and build a static GMP (incl. C++)
brew install gmp
mkdir -p $BUILD_DIR && cd $BUILD_DIR && mkdir -p dynamic_gmp && mkdir -p static_gmp
wget https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz
tar xf gmp-6.3.0.tar.xz 
cd gmp-6.3.0
./configure --enable-cxx --enable-static --disable-shared --prefix=$BUILD_DIR/static_gmp CC=gcc ABI=64 CFLAGS='-fPIC -m64' CPPFLAGS=-DPIC
make && make install
# make check

# Build (custom) PPL with Java interface
mkdir -p $BUILD_DIR && cd $BUILD_DIR
git clone https://github.com/prismmodelchecker/ppl.git
cd ppl
autoreconf -i
./configure --enable-interfaces=java --with-java="$JAVA_HOME" --disable-documentation --prefix=$BUILD_DIR --with-gmp-include="$BUILD_DIR/static_gmp/include" --with-gmp-lib-static="$BUILD_DIR/static_gmp/lib/libgmpxx.a $BUILD_DIR/static_gmp/lib/libgmp.a" CXXFLAGS="-std=c++11" JAVACFLAGS="--release 9"
make
make install
cp $BUILD_DIR/lib/libppl.15.dylib $BUILD_DIR/lib/ppl/libppl_java.jnilib $BUILD_DIR/lib/ppl/ppl_java.jar $BUILD_DIR
```

Files `libppl.15.dylib`, `libppl_java.jnilib` an `ppl_java.jar` are then in `$BUILD_DIR`.

---

### Cygwin

(assuming set up as in prism-install-win.bat/prism-install-cygwin)

```
# Pre-requisites
/cygdrive/c/Users/Administrator/setup-x86_64 -P autoconf -P automake -P libtool -q

# Set-up
export BUILD_DIR="/tools2"
export JAVA_HOME="/cygdrive/c/Program Files/Eclipse Adoptium/jdk-11.0.21.9-hotspot"
export LIBWINPTHREAD_DIR="/cygdrive/c/cygwin64/usr/x86_64-w64-mingw32/sys-root/mingw/bin"

# Java install without spaces (PPL configure breaks)
mkdir -p "$BUILD_DIR" && ln -s "$JAVA_HOME" "$BUILD_DIR/java"
export JAVA_HOME2="$BUILD_DIR/java"

# Build a dynamic GMP and a static GMP (incl. C++)
mkdir -p $BUILD_DIR && cd $BUILD_DIR && mkdir -p dynamic_gmp && mkdir -p static_gmp
wget https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz
tar xf gmp-6.3.0.tar.xz 
cd gmp-6.3.0
./configure --host=x86_64-w64-mingw32 --build=i686-pc-cygwin --enable-cxx LDFLAGS="-Wl,--add-stdcall-alias" --enable-shared --disable-static --prefix=$BUILD_DIR/dynamic_gmp
make && make install
# make check
make clean
./configure --host=x86_64-w64-mingw32 --build=i686-pc-cygwin --enable-cxx LDFLAGS="-Wl,--add-stdcall-alias" --enable-static --disable-shared --prefix=$BUILD_DIR/static_gmp
make && make install
# make check

# Build (custom) PPL with Java interface
mkdir -p $BUILD_DIR && cd $BUILD_DIR
git clone https://github.com/prismmodelchecker/ppl.git
cd ppl
autoreconf -i
./configure --host=x86_64-w64-mingw32 --enable-interfaces=java --with-java="$JAVA_HOME2" --disable-documentation --prefix=$BUILD_DIR --with-gmp-include="$BUILD_DIR/static_gmp/include" --with-gmp-lib-static="$BUILD_DIR/static_gmp/lib/libgmpxx.a $BUILD_DIR/static_gmp/lib/libgmp.a" CXXFLAGS="-std=c++11" LDFLAGS="-static-libgcc -static-libstdc++ -Wl,--add-stdcall-alias -Wl,-Bstatic,--whole-archive -lpthread -Wl,-Bdynamic,--no-whole-archive" JAVACFLAGS="--release 9"

./configure --host=x86_64-w64-mingw32 --enable-interfaces=java --with-java="$JAVA_HOME2" --disable-documentation --prefix=$BUILD_DIR --with-gmp-include="$BUILD_DIR/static_gmp/include" --with-gmp-lib-static="$BUILD_DIR/static_gmp/lib/libgmpxx_pic.a $BUILD_DIR/static_gmp/lib/libgmp_pic.a" CXXFLAGS="-std=c++11" LDFLAGS="-static-libgcc -static-libstdc++ -Wl,--add-stdcall-alias -Wl,-Bstatic,--whole-archive -lpthread -Wl,-Bdynamic,--no-whole-archive -L$LIBWINPTHREAD_DIR" JAVACFLAGS="--release 9"

./configure --host=x86_64-w64-mingw32 --enable-interfaces=java --with-java="$JAVA_HOME2" --disable-documentation --prefix=$BUILD_DIR --with-gmp-include="$BUILD_DIR/static_gmp/include" --with-gmp-lib-static="$BUILD_DIR/static_gmp/lib/libgmpxx.a $BUILD_DIR/static_gmp/lib/libgmp.a" CXXFLAGS="-std=c++11" LDFLAGS="-static-libgcc -static-libstdc++ -Wl,--add-stdcall-alias -Wl,-Bstatic,--whole-archive -lpthread -Wl,-Bdynamic,--no-whole-archive -L$LIBWINPTHREAD_DIR" JAVACFLAGS="--release 9"


make
make install
cp $BUILD_DIR/lib/libppl.15.dylib $BUILD_DIR/lib/ppl/libppl_java.jnilib $BUILD_DIR/lib/ppl/ppl_java.jar $BUILD_DIR


# Build Yices 2
mkdir -p $BUILD_DIR && cd $BUILD_DIR
wget https://yices.csl.sri.com/releases/2.6.4/yices-2.6.4-src.tar.gz
tar xfz yices-2.6.4-src.tar.gz
cd yices2-Yices-2.6.4
autoconf
./configure --host=x86_64-w64-mingw32 CPPFLAGS=-I$BUILD_DIR/dynamic_gmp/include LDFLAGS="-L$BUILD_DIR/dynamic_gmp/lib -Wl,--add-stdcall-alias" --with-static-gmp=$BUILD_DIR/static_gmp/lib/libgmp.a --with-static-gmp-include-dir=$BUILD_DIR/static_gmp/include
export LD_LIBRARY_PATH=/usr/local/lib/:${LD_LIBRARY_PATH}
make OPTION=mingw64 MODE=release static-dist
cp build/*/static_dist/bin/libyices.dll $BUILD_DIR
cp build/*/static_dist/include/* $BUILD_DIR

# Build Yices Java bindings
mkdir -p $BUILD_DIR && cd $BUILD_DIR
git clone https://github.com/SRI-CSL/yices2_java_bindings
cd yices2_java_bindings
cd src/main/java/com/sri/yices
javac --release 9 -d ../../../../../../dist/lib -h . *.java
x86_64-w64-mingw32-g++ -I$JAVA_HOME/include -I$JAVA_HOME/include/win32 -I$BUILD_DIR -I$BUILD_DIR/static_gmp/include -fpermissive -c yicesJNIforWindows.cpp
x86_64-w64-mingw32-g++ -shared -static-libgcc -static-libstdc++ -Wl,--add-stdcall-alias -Wl,-Bstatic,--whole-archive -lpthread -Wl,-Bdynamic,--no-whole-archive -o yices2java.dll yicesJNIforWindows.o $BUILD_DIR/static_gmp/lib/libgmp.a -L$BUILD_DIR -lyices
cd ../../../../../..
jar cvfm yices.jar MANIFEST.txt -C dist/lib .
cp src/main/java/com/sri/yices/yices2java.dll yices.jar $BUILD_DIR
```

Files `libyices.dll`, `yices2java.dll` and `yices.jar` are then in `$BUILD_DIR`.

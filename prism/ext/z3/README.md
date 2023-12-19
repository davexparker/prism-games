This directory contains pre-compiled versions of Z3 and its Java interface.
The Makefile copies the correct versions to the PRISM lib directory.

---

We take files directly from the Z3 binary releases at

https://github.com/Z3Prover/z3/releases

(except for Linux aarch64, which was built from source)

* libz3.{so,dylib,dll}
* libz3java.{so,dylib,dll}
* com.microsoft.z3.jar

---

Instructions for building from source (e.g. on Linux):

```
wget https://github.com/Z3Prover/z3/archive/z3-4.12.4.tar.gz
tar xvfz z3-4.12.4.tar.gz
cd z3-z3-4.12.4
python scripts/mk_make.py --java
cd build
make
sudo make install
```

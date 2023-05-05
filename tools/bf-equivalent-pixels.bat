@echo off

rem bf-equivalent-pixels.bat: a batch file for testing if two datasets have images with equivalent pixel values

rem Required JARs: bioformats_package.jar, bio-formats-testing-framework.jar

setlocal
set BF_DIR=%~dp0
if "%BF_DIR:~-1%" == "\" set BF_DIR=%BF_DIR:~0,-1%

set BF_PROG=loci.tests.testng.EquivalentPixelsTest
call "%BF_DIR%\bf.bat" %*

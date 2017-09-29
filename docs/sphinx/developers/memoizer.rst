Caching reader state
====================

Initializing a Bio-Formats reader can consume substantial time and memory.
Most of the initialization time is spend in the
:javadoc:`setId(java.lang.String) <loci/formats/IFormatHandler.html#setId-java.lang.String->`
call. Various factors can impact the performance of this step including the
file size, the amount of metadata in the image and also the file format itself.

One solution to improve reading performance is to use Bio-Formats memoization
functionalities with the
:javadoc:`loci.formats.Memoizer <loci/formats/Memoizer.html>` reader wrapper.
By essence, the speedup gained from memoization will only happen after the
first initialization of the reader for a particular file.

The simplest way to make use the ``Memoizer`` functionalities is
illustrated by the following example:

.. literalinclude:: examples/MemoizedReader.java
  :language: java

If the time required to call :javadoc:`setId(java.lang.String) <loci/formats/Memoizer.html#setId-java.lang.String->` method is larger
than :javadoc:`DEFAULT_MINIMUM_ELAPSED <loci/formats/Memoizer.html#DEFAULT_MINIMUM_ELAPSED>` or the minimum value
passed in the constructor, the initialized reader will be cached in a memo
file under the same folder as the input file, or the folder specified by the constructor. Any subsequent call to
``setId()`` with a reader decorated by the ``Memoizer`` on the same input file
will load the reader from the memo file instead of performing a full reader
initialization.

More constructors are described in the
:javadoc:`Memoizer javadocs <loci/formats/Memoizer.html>` allowing to control
the minimal initialization time required before caching the reader and/or to
define a root directory under which the reader should be cached.

//
// LuraWaveCodec.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.codec;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import loci.common.ReflectException;
import loci.common.ReflectedUniverse;
import loci.formats.FormatException;
import loci.formats.MissingLibraryException;

/**
 * This class provides LuraWave decompression, using LuraWave's Java decoding
 * library. Compression is not supported. Decompression requires a LuraWave
 * license code, specified in the lurawave.license system property (e.g.,
 * <code>-Dlurawave.license=XXXX</code> on the command line).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/codec/LuraWaveCodec.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/codec/LuraWaveCodec.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class LuraWaveCodec extends BaseCodec {

  // -- Constants --

  /** System property to check for the LuraWave license code. */
  public static final String LICENSE_PROPERTY = "lurawave.license";

  /** Message displayed if the LuraWave LWF decoder library is not found. */
  public static final String NO_LURAWAVE_MSG =
    "The LuraWave decoding library, lwf_jsdk2.6.jar, is required to decode " +
    "this file.\r\nPlease make sure it is present in your classpath.";

  /** Message to display if no LuraWave license code is given. */
  public static final String NO_LICENSE_MSG =
    "No LuraWave license code was specified.\r\nPlease set one in the " +
    LICENSE_PROPERTY + " system property (e.g., with -D" + LICENSE_PROPERTY +
    "=XXXX from the command line).";

  /** Message to display if an invalid LuraWave license code is given. */
  public static final String INVALID_LICENSE_MSG = "Invalid license code: ";

  // -- Static fields --

  /** True iff the LuraWave decoding library is not available. */
  protected static boolean noLuraWave;

  /** License code for LuraWave decoding library. */
  protected static String licenseCode;

  /** Reflected universe for LuraWave decoding library calls. */
  private static ReflectedUniverse r;

  // -- Static initializer --

  static {
    r = new ReflectedUniverse();
    try {
      r.exec("import com.luratech.lwf.lwfDecoder");
      r.setVar("-1", -1);
      r.setVar("1", 1);
      r.setVar("1024", 1024);
      r.setVar("0", 0);
    }
    catch (ReflectException exc) {
      noLuraWave = true;
    }
  }

  // -- Codec API methods --

  /* @see Codec#compress(byte[], CodecOptions) */
  public byte[] compress(byte[] data, CodecOptions options)
    throws FormatException
  {
    throw new FormatException("LuraWave compression not supported");
  }

  /* @see Codec#decompress(RandomAccessInputStream, CodecOptions) */
  public byte[] decompress(RandomAccessInputStream in, CodecOptions options)
    throws FormatException, IOException
  {
    byte[] buf = new byte[(int) in.length()];
    in.read(buf);
    return decompress(buf, options);
  }

  /**
   * The CodecOptions parameter should have the following fields set:
   *  {@link CodecOptions#maxBytes maxBytes}
   *
   * @see Codec#decompress(byte[], CodecOptions)
   */
  public byte[] decompress(byte[] buf, CodecOptions options)
    throws FormatException
  {
    if (noLuraWave) throw new MissingLibraryException(NO_LURAWAVE_MSG);
    licenseCode = System.getProperty(LICENSE_PROPERTY);
    if (licenseCode == null) throw new FormatException(NO_LICENSE_MSG);
    r.setVar("stream",
      new BufferedInputStream(new ByteArrayInputStream(buf), 4096));
    try {
      r.setVar("licenseCode", licenseCode);
      r.exec("lwf = new lwfDecoder(stream, null, licenseCode)");
    }
    catch (ReflectException exc) {
      throw new FormatException(INVALID_LICENSE_MSG + licenseCode, exc);
    }

    int w = 0, h = 0;

    try {
      w = ((Integer) r.exec("lwf.getWidth()")).intValue();
      h = ((Integer) r.exec("lwf.getHeight()")).intValue();
    }
    catch (ReflectException exc) {
      throw new FormatException("Could not retrieve image dimensions", exc);
    }

    int nbits = 8 * (options.maxBytes / (w * h));

    if (nbits == 8) {
      byte[] image8 = new byte[w * h];
      try {
        r.setVar("image8", image8);
        r.exec("lwf.decodeToMemoryGray8(image8, -1, 1024, 0)");
      }
      catch (ReflectException exc) {
        throw new FormatException("Could not decode LuraWave data", exc);
      }
      return image8;
    }
    else if (nbits == 16) {
      short[] image16 = new short[w * h];
      try {
        r.setVar("image16", image16);
        r.setVar("w", w);
        r.setVar("h", h);
        r.exec("lwf.decodeToMemoryGray16(image16, " +
          "0, -1, 1024, 0, 1, w, 0, 0, w, h)");
      }
      catch (ReflectException exc) {
        throw new FormatException("Could not decode LuraWave data", exc);
      }

      byte[] output = new byte[w * h * 2];
      for (int i=0; i<image16.length; i++) {
        DataTools.unpackBytes(image16[i], output, i * 2, 2, true);
      }
      return output;
    }

    throw new FormatException("Unsupported bits per pixel: " + nbits);
  }

}
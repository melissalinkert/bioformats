//
// AVIWriter.java
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

package loci.formats.out;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.RandomAccessOutputStream;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.FormatWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataRetrieve;

/**
 * AVIWriter is the file format writer for AVI files.
 *
 * Much of this writer's code was adapted from Wayne Rasband's
 * AVI Movie Writer plugin for ImageJ
 * (available at http://rsb.info.nih.gov/ij/).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/out/AVIWriter.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/out/AVIWriter.java">SVN</a></dd></dl>
 */
public class AVIWriter extends FormatWriter {

  // -- Fields --

  private RandomAccessOutputStream out;

  private int planesWritten = 0;

  private int bytesPerPixel;
  private File file;
  private int xDim, yDim, zDim, tDim, xPad;
  private int microSecPerFrame;

  // location of file size in bytes not counting first 8 bytes
  private long saveFileSize;

  // location of length of CHUNK with first LIST - not including first 8
  // bytes with LIST and size. JUNK follows the end of this CHUNK
  private long saveLIST1Size;

  // location of length of CHUNK with second LIST - not including first 8
  // bytes with LIST and size. Note that saveLIST1subSize = saveLIST1Size +
  // 76, and that the length size written to saveLIST2Size is 76 less than
  // that written to saveLIST1Size. JUNK follows the end of this CHUNK.
  private long saveLIST1subSize;

  // location of length of strf CHUNK - not including the first 8 bytes with
  // strf and size. strn follows the end of this CHUNK.
  private long savestrfSize;

  private long savestrnPos;
  private long saveJUNKsignature;
  private int paddingBytes;
  private long saveLIST2Size;
  private byte[] dataSignature;
  private Vector savedbLength;
  private long idx1Pos;
  private long endPos;
  private long saveidx1Length;
  private int z;
  private long savemovi;
  private int xMod;
  private long frameOffset;
  private long frameOffset2;

  // -- Constructor --

  public AVIWriter() { super("Audio Video Interleave", "avi"); }

  // -- IFormatWriter API methods --

  /* @see loci.formats.IFormatWriter#saveBytes(byte[], int, boolean, boolean) */
  public void saveBytes(byte[] buf, int series, boolean lastInSeries,
    boolean last) throws FormatException, IOException
  {
    if (buf == null) {
      throw new FormatException("Byte array is null");
    }
    MetadataRetrieve meta = getMetadataRetrieve();
    MetadataTools.verifyMinimumPopulated(meta, series);
    int type =
      FormatTools.pixelTypeFromString(meta.getPixelsPixelType(series, 0));
    if (!DataTools.containsValue(getPixelTypes(), type)) {
      throw new FormatException("Unsupported image type '" +
        FormatTools.getPixelTypeString(type) + "'.");
    }

    Integer channels = meta.getLogicalChannelSamplesPerPixel(series, 0);
    if (channels == null) {
      warn("SamplesPerPixel #0 is null.  It is assumed to be 1.");
    }
    int nChannels = channels == null ? 1 : channels.intValue();

    byte[][] lut = null;

    if (getColorModel() instanceof IndexColorModel) {
      lut = new byte[4][256];
      IndexColorModel model = (IndexColorModel) getColorModel();
      model.getReds(lut[0]);
      model.getGreens(lut[1]);
      model.getBlues(lut[2]);
      model.getAlphas(lut[3]);
    }

    if (!initialized) {
      initialized = true;
      planesWritten = 0;
      bytesPerPixel = nChannels == 2 ? 3 : nChannels;

      out = new RandomAccessOutputStream(currentId);
      out.seek(out.length());
      saveFileSize = 4;
      saveLIST1Size = 16;
      saveLIST1subSize = 23 * 4;
      frameOffset = 48;
      frameOffset2 = 35 * 4;
      savestrfSize = 42 * 4;
      savestrnPos = savestrfSize + 44 + (bytesPerPixel == 1 ? 4 * 256 : 0);
      saveJUNKsignature = savestrnPos + 24;
      saveLIST2Size = 4088;
      savemovi = 4092;

      savedbLength = new Vector();

      dataSignature = new byte[4];
      dataSignature[0] = 48; // 0
      dataSignature[1] = 48; // 0
      dataSignature[2] = 100; // d
      dataSignature[3] = 98; // b

      tDim = 1;
      zDim = 1;
      yDim = meta.getPixelsSizeY(series, 0).intValue();
      xDim = meta.getPixelsSizeX(series, 0).intValue();

      xPad = 0;
      xMod = xDim % 4;
      if (xMod != 0) {
        xPad = 4 - xMod;
        xDim += xPad;
      }

      if (out.length() == 0) {
        DataTools.writeString(out, "RIFF"); // signature
        // Bytes 4 thru 7 contain the length of the file. This length does
        // not include bytes 0 thru 7.
        DataTools.writeInt(out, 0, true); // for now write 0 for size
        DataTools.writeString(out, "AVI "); // RIFF type
        // Write the first LIST chunk, which contains
        // information on data decoding
        DataTools.writeString(out, "LIST"); // CHUNK signature
        // Write the length of the LIST CHUNK not including the first 8 bytes
        // with LIST and size. Note that the end of the LIST CHUNK is followed
        // by JUNK.
        DataTools.writeInt(out, (bytesPerPixel == 1) ? 1240 : 216, true);
        DataTools.writeString(out, "hdrl"); // CHUNK type
        DataTools.writeString(out, "avih"); // Write the avih sub-CHUNK

        // Write the length of the avih sub-CHUNK (38H) not including the
        // the first 8 bytes for avihSignature and the length
        DataTools.writeInt(out, 0x38, true);

        // dwMicroSecPerFrame - Write the microseconds per frame
        microSecPerFrame = (int) (1.0 / fps * 1.0e6);
        DataTools.writeInt(out, microSecPerFrame, true);

        // Write the maximum data rate of the file in bytes per second
        DataTools.writeInt(out, 0, true); // dwMaxBytesPerSec

        DataTools.writeInt(out, 0, true); // dwReserved1 - set to 0
        // dwFlags - just set the bit for AVIF_HASINDEX
        DataTools.writeInt(out, 0x10, true);

        // 10H AVIF_HASINDEX: The AVI file has an idx1 chunk containing
        //   an index at the end of the file. For good performance, all
        //   AVI files should contain an index.
        // 20H AVIF_MUSTUSEINDEX: Index CHUNK, rather than the physical
        // ordering of the chunks in the file, must be used to determine the
        // order of the frames.
        // 100H AVIF_ISINTERLEAVED: Indicates that the AVI file is interleaved.
        //   This is used to read data from a CD-ROM more efficiently.
        // 800H AVIF_TRUSTCKTYPE: USE CKType to find key frames
        // 10000H AVIF_WASCAPTUREFILE: The AVI file is used for capturing
        //   real-time video. Applications should warn the user before
        //   writing over a file with this fla set because the user
        //   probably defragmented this file.
        // 20000H AVIF_COPYRIGHTED: The AVI file contains copyrighted data
        //   and software. When, this flag is used, software should not
        //   permit the data to be duplicated.

        // dwTotalFrames - total frame number
        DataTools.writeInt(out, zDim * tDim, true);

        // dwInitialFrames -Initial frame for interleaved files.
        // Noninterleaved files should specify 0.
        DataTools.writeInt(out, 0, true);

        // dwStreams - number of streams in the file - here 1 video and
        // zero audio.
        DataTools.writeInt(out, 1, true);

        // dwSuggestedBufferSize - Suggested buffer size for reading the file.
        // Generally, this size should be large enough to contain the largest
        // chunk in the file.
        DataTools.writeInt(out, 0, true);

        // dwWidth - image width in pixels
        DataTools.writeInt(out, xDim - xPad, true);
        DataTools.writeInt(out, yDim, true); // dwHeight - height in pixels

        // dwReserved[4] - Microsoft says to set the following 4 values to 0.
        DataTools.writeInt(out, 0, true);
        DataTools.writeInt(out, 0, true);
        DataTools.writeInt(out, 0, true);
        DataTools.writeInt(out, 0, true);

        // Write the Stream line header CHUNK
        DataTools.writeString(out, "LIST");

        // Write the size of the first LIST subCHUNK not including the first 8
        // bytes with LIST and size. Note that saveLIST1subSize = saveLIST1Size
        // + 76, and that the length written to saveLIST1subSize is 76 less than
        // the length written to saveLIST1Size. The end of the first LIST
        // subCHUNK is followed by JUNK.

        DataTools.writeInt(out, (bytesPerPixel == 1) ? 1164 : 140 , true);
        DataTools.writeString(out, "strl");   // Write the chunk type
        DataTools.writeString(out, "strh"); // Write the strh sub-CHUNK
        DataTools.writeInt(out, 56, true); // Write length of strh sub-CHUNK

        // fccType - Write the type of data stream - here vids for video stream
        DataTools.writeString(out, "vids");

        // Write DIB for Microsoft Device Independent Bitmap.
        // Note: Unfortunately, at least 3 other four character codes are
        // sometimes used for uncompressed AVI videos: 'RGB ', 'RAW ',
        // 0x00000000
        DataTools.writeString(out, "DIB ");

        DataTools.writeInt(out, 0, true); // dwFlags

        // 0x00000001 AVISF_DISABLED The stram data should be rendered only when
        // explicitly enabled.
        // 0x00010000 AVISF_VIDEO_PALCHANGES Indicates that a palette change is
        // included in the AVI file. This flag warns the playback software that
        // it will need to animate the palette.

        // dwPriority - priority of a stream type. For example, in a file with
        // multiple audio streams, the one with the highest priority might be
        // the default one.
        DataTools.writeInt(out, 0, true);

        // dwInitialFrames - Specifies how far audio data is skewed ahead of
        // video frames in interleaved files. Typically, this is about 0.75
        // seconds. In interleaved files specify the number of frames in the
        // file prior to the initial frame of the AVI sequence.
        // Noninterleaved files should use zero.
        DataTools.writeInt(out, 0, true);

        // rate/scale = samples/second
        DataTools.writeInt(out, 1, true); // dwScale

        //  dwRate - frame rate for video streams
        DataTools.writeInt(out, fps, true);

        // dwStart - this field is usually set to zero
        DataTools.writeInt(out, 0, true);

        // dwLength - playing time of AVI file as defined by scale and rate
        // Set equal to the number of frames
        DataTools.writeInt(out, tDim * zDim, true);

        // dwSuggestedBufferSize - Suggested buffer size for reading the stream.
        // Typically, this contains a value corresponding to the largest chunk
        // in a stream.
        DataTools.writeInt(out, 0, true);

        // dwQuality - encoding quality given by an integer between 0 and
        // 10,000. If set to -1, drivers use the default quality value.
        DataTools.writeInt(out, -1, true);

        // dwSampleSize #
        // 0 if the video frames may or may not vary in size
        // If 0, each sample of data(such as a video frame) must be in a
        // separate chunk. If nonzero, then multiple samples of data can be
        // grouped into a single chunk within the file.
        DataTools.writeInt(out, 0, true);

        // rcFrame - Specifies the destination rectangle for a text or video
        // stream within the movie rectangle specified by the dwWidth and
        // dwHeight members of the AVI main header structure. The rcFrame member
        // is typically used in support of multiple video streams. Set this
        // rectangle to the coordinates corresponding to the movie rectangle to
        // update the whole movie rectangle. Units for this member are pixels.
        // The upper-left corner of the destination rectangle is relative to the
        // upper-left corner of the movie rectangle.
        DataTools.writeShort(out, (short) 0, true); // left
        DataTools.writeShort(out, (short) 0, true); // top
        DataTools.writeShort(out, (short) 0, true); // right
        DataTools.writeShort(out, (short) 0, true); // bottom

        // Write the size of the stream format CHUNK not including the first 8
        // bytes for strf and the size. Note that the end of the stream format
        // CHUNK is followed by strn.
        DataTools.writeString(out, "strf"); // Write the stream format chunk

        // write the strf CHUNK size
        DataTools.writeInt(out, (bytesPerPixel == 1) ? 1068 : 44, true);

        // Applications should use this size to determine which BITMAPINFO
        // header structure is being used. This size includes this biSize field.
        // biSize- Write header size of BITMAPINFO header structure

        DataTools.writeInt(out, 40, true);

        // biWidth - image width in pixels
        DataTools.writeInt(out, xDim, true);

        // biHeight - image height in pixels. If height is positive, the bitmap
        // is a bottom up DIB and its origin is in the lower left corner. If
        // height is negative, the bitmap is a top-down DIB and its origin is
        // the upper left corner. This negative sign feature is supported by the
        // Windows Media Player, but it is not supported by PowerPoint.
        DataTools.writeInt(out, yDim, true);

        // biPlanes - number of color planes in which the data is stored
        // This must be set to 1.
        DataTools.writeShort(out, 1, true);

        int bitsPerPixel = (bytesPerPixel == 3) ? 24 : 8;

        // biBitCount - number of bits per pixel #
        // 0L for BI_RGB, uncompressed data as bitmap
        DataTools.writeShort(out, (short) bitsPerPixel, true);

        //writeInt(bytesPerPixel * xDim * yDim * zDim * tDim); // biSizeImage #
        DataTools.writeInt(out, 0, true); // biSizeImage #
        DataTools.writeInt(out, 0, true); // biCompression - compression type
        // biXPelsPerMeter - horizontal resolution in pixels
        DataTools.writeInt(out, 0, true);
        // biYPelsPerMeter - vertical resolution in pixels per meter
        DataTools.writeInt(out, 0, true);

        int nColors = lut == null ? 0 : 256;
        DataTools.writeInt(out, nColors, true);

        // biClrImportant - specifies that the first x colors of the color table
        // are important to the DIB. If the rest of the colors are not
        // available, the image still retains its meaning in an acceptable
        // manner. When this field is set to zero, all the colors are important,
        // or, rather, their relative importance has not been computed.
        DataTools.writeInt(out, 0, true);

        // Write the LUTa.getExtents()[1] color table entries here. They are
        // written: blue byte, green byte, red byte, 0 byte
        if (bytesPerPixel == 1) {
          if (lut != null) {
            for (int i=0; i<256; i++) {
              out.write(lut[2][i]);
              out.write(lut[1][i]);
              out.write(lut[0][i]);
              out.write(lut[3][i]);
            }
          }
          else {
            byte[] lutWrite = new byte[4 * 256];
            for (int i=0; i<256; i++) {
              lutWrite[4*i] = (byte) i; // blue
              lutWrite[4*i+1] = (byte) i; // green
              lutWrite[4*i+2] = (byte) i; // red
              lutWrite[4*i+3] = 0;
            }
            out.write(lutWrite);
          }
        }

        out.seek(savestrfSize);
        DataTools.writeInt(out,
          (int) (savestrnPos - (savestrfSize + 4)), true);
        out.seek(savestrnPos);

        // Use strn to provide zero terminated text string describing the stream
        DataTools.writeString(out, "strn");
        DataTools.writeInt(out, 16, true); // Write length of strn sub-CHUNK
        DataTools.writeString(out, "FileAVI write  ");

        out.seek(saveLIST1Size);
        DataTools.writeInt(out,
          (int) (saveJUNKsignature - (saveLIST1Size + 4)), true);
        out.seek(saveLIST1subSize);
        DataTools.writeInt(out,
          (int) (saveJUNKsignature - (saveLIST1subSize + 4)), true);
        out.seek(saveJUNKsignature);

        // write a JUNK CHUNK for padding
        DataTools.writeString(out, "JUNK");
        paddingBytes = (int) (4084 - (saveJUNKsignature + 8));
        DataTools.writeInt(out, paddingBytes, true);
        for (int i=0; i<paddingBytes/2; i++) {
          DataTools.writeShort(out, (short) 0, true);
        }

        // Write the second LIST chunk, which contains the actual data
        DataTools.writeString(out, "LIST");

        // Write the length of the LIST CHUNK not including the first 8 bytes
        // with LIST and size. The end of the second LIST CHUNK is followed by
        // idx1.
        saveLIST2Size = out.getFilePointer();

        DataTools.writeInt(out, 0, true);  // For now write 0
        DataTools.writeString(out, "movi"); // Write CHUNK type 'movi'
      }
    }

    // Write the data. Each 3-byte triplet in the bitmap array represents the
    // relative intensities of blue, green, and red, respectively, for a pixel.
    // The color bytes are in reverse order from the Windows convention.

    int width = xDim - xPad;
    int height = buf.length / (width * bytesPerPixel);

    out.write(dataSignature);
    savedbLength.add(new Long(out.getFilePointer()));

    // Write the data length
    DataTools.writeInt(out, bytesPerPixel * xDim * yDim, true);

    int rowPad = xPad * bytesPerPixel;

    byte[] rowBuffer = new byte[width * bytesPerPixel + rowPad];

    for (int row=height-1; row>=0; row--) {
      for (int col=0; col<width; col++) {
        int offset = row * width + col;
        if (interleaved) offset *= nChannels;
        byte r = buf[offset];
        if (nChannels > 1) {
          byte g = buf[offset + (interleaved ? 1 : width * height)];
          byte b = 0;
          if (nChannels > 2) {
            b = buf[offset + (interleaved ? 2 : 2 * width * height)];
          }

          rowBuffer[col * bytesPerPixel] = b;
          rowBuffer[col * bytesPerPixel + 1] = g;
        }
        rowBuffer[col * bytesPerPixel + bytesPerPixel - 1] = r;
      }
      out.write(rowBuffer);
    }

    planesWritten++;

    if (last) {
      // Write the idx1 CHUNK
      // Write the 'idx1' signature
      idx1Pos = out.getFilePointer();
      out.seek(saveLIST2Size);
      DataTools.writeInt(out, (int) (idx1Pos - (saveLIST2Size + 4)), true);
      out.seek(idx1Pos);
      DataTools.writeString(out, "idx1");

      saveidx1Length = out.getFilePointer();

      // Write the length of the idx1 CHUNK not including the idx1 signature
      DataTools.writeInt(out, 4 + (planesWritten*16), true);

      for (z=0; z<planesWritten; z++) {
        // In the ckid field write the 4 character code to identify the chunk
        // 00db or 00dc
        out.write(dataSignature);
        // Write the flags - select AVIIF_KEYFRAME
        if (z == 0) DataTools.writeInt(out, 0x10, true);
        else DataTools.writeInt(out, 0x00, true);

        // AVIIF_KEYFRAME 0x00000010L
        // The flag indicates key frames in the video sequence.
        // Key frames do not need previous video information to be
        // decompressed.
        // AVIIF_NOTIME 0x00000100L The CHUNK does not influence video timing
        // (for example a palette change CHUNK).
        // AVIIF_LIST 0x00000001L Marks a LIST CHUNK.
        // AVIIF_TWOCC 2L
        // AVIIF_COMPUSE 0x0FFF0000L These bits are for compressor use.
        DataTools.writeInt(out, (int) (((Long)
          savedbLength.get(z)).longValue() - 4 - savemovi), true);

        // Write the offset (relative to the 'movi' field) to the relevant
        // CHUNK. Write the length of the relevant CHUNK. Note that this length
        // is also written at savedbLength
        DataTools.writeInt(out, bytesPerPixel*xDim*yDim, true);
      }
      endPos = out.getFilePointer();
      out.seek(saveFileSize);
      DataTools.writeInt(out, (int) (endPos - (saveFileSize + 4)), true);

      out.seek(saveidx1Length);
      DataTools.writeInt(out, (int) (endPos - (saveidx1Length + 4)), true);

      // write the total number of planes
      out.seek(frameOffset);
      DataTools.writeInt(out, planesWritten, true);
      out.seek(frameOffset2);
      DataTools.writeInt(out, planesWritten, true);

      out.close();
    }
  }

  /* @see loci.formats.IFormatWriter#canDoStacks() */
  public boolean canDoStacks() { return true; }

  /* @see loci.formats.IFormatWriter#getPixelTypes() */
  public int[] getPixelTypes() {
    return new int[] {FormatTools.UINT8};
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    if (out != null) out.close();
    out = null;
    currentId = null;
    initialized = false;
  }

}
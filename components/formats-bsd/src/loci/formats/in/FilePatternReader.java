/*
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * Copyright (C) 2005 - 2017 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.formats.in;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import loci.common.DataTools;
import loci.common.Location;
import loci.formats.ClassList;
import loci.formats.CoreMetadata;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.Modulo;
import loci.formats.meta.MetadataStore;

/**
 *
 */
public class FilePatternReader extends FormatReader {

  // -- Fields --

  private FileStitcher stitcher;
  private IFormatReader helper;
  private String pattern;
  private ClassList<IFormatReader> newClasses;
  private String[] files;
  private int[][][] fileIndexes;

  // -- Constructor --

  /** Constructs a new pattern reader. */
  public FilePatternReader() {
    super("File pattern", new String[] {"pattern"});

    ClassList<IFormatReader> classes = ImageReader.getDefaultReaderClasses();
    Class<? extends IFormatReader>[] classArray = classes.getClasses();
    newClasses = new ClassList<IFormatReader>(IFormatReader.class);
    for (Class<? extends IFormatReader> c : classArray) {
      if (!c.equals(FilePatternReader.class)) {
        newClasses.addClass(c);
      }
    }
    stitcher = new FileStitcher(new ImageReader(newClasses));
    helper = Memoizer.wrap(getMetadataOptions(), stitcher);

    suffixSufficient = true;
  }

  // -- IFormatReader methods --

  @Override
  public int getImageCount() {
    return helper.getImageCount();
  }

  @Override
  public boolean isRGB() {
    return helper.isRGB();
  }

  @Override
  public int getSizeX() {
    return helper.getSizeX();
  }

  @Override
  public int getSizeY() {
    return helper.getSizeY();
  }

  @Override
  public int getSizeZ() {
    return helper.getSizeZ();
  }

  @Override
  public int getSizeC() {
    return helper.getSizeC();
  }

  @Override
  public int getSizeT() {
    return helper.getSizeT();
  }

  @Override
  public int getPixelType() {
    return helper.getPixelType();
  }

  @Override
  public int getBitsPerPixel() {
    return helper.getBitsPerPixel();
  }

  @Override
  public int getEffectiveSizeC() {
    return helper.getEffectiveSizeC();
  }

  @Override
  public int getRGBChannelCount() {
    return helper.getRGBChannelCount();
  }

  @Override
  public boolean isIndexed() {
    return helper.isIndexed();
  }

  @Override
  public boolean isFalseColor() {
    return helper.isFalseColor();
  }

  @Override
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    if (getCurrentFile() == null) {
      return null;
    }
    return helper.get8BitLookupTable();
  }

  @Override
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    if (getCurrentFile() == null) {
      return null;
    }
    return helper.get16BitLookupTable();
  }

  @Override
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  @Override
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    byte[] buf = new byte[FormatTools.getPlaneSize(this, w, h)];
    return openBytes(no, buf, x, y, w, h);
  }

  @Override
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return openBytes(no, buf, 0, 0, getSizeX(), getSizeY());
  }

  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    int fileIndex = fileIndexes[getSeries()][no][0];
    int planeIndex = fileIndexes[getSeries()][no][1];
    helper.close();
    helper.setId(files[fileIndex]);
    byte[] plane = null;
    try {
      helper.setSeries(getSeries());
      plane = helper.openBytes(planeIndex, buf, x, y, w, h);
    } finally {
      helper.close();
    }
    return plane;
  }

  @Override
  public Object openPlane(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return helper.openPlane(no, x, y, w, h);
  }

  @Override
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    return helper.openThumbBytes(no);
  }

  @Override
  public void close(boolean fileOnly) throws IOException {
    helper.close(fileOnly);
    if (!fileOnly) {
      pattern = null;
      files = null;
      fileIndexes = null;
    }
  }

  @Override
  public void setGroupFiles(boolean group) {
    helper.setGroupFiles(group);
  }

  @Override
  public boolean isGroupFiles() {
    return helper.isGroupFiles();
  }

  @Override
  public void setNormalized(boolean normalize) {
    helper.setNormalized(normalize);
  }

  @Override
  public boolean isNormalized() { return helper.isNormalized(); }

  @Override
  public void setOriginalMetadataPopulated(boolean populate) {
    helper.setOriginalMetadataPopulated(populate);
  }

  @Override
  public boolean isOriginalMetadataPopulated() {
    return helper.isOriginalMetadataPopulated();
  }

  @Override
  public String[] getSeriesUsedFiles(boolean noPixels) {
    if (noPixels) {
      return new String[] {currentId};
    }
    String[] allFiles = new String[files.length + 1];
    allFiles[0] = currentId;
    System.arraycopy(files, 0, allFiles, 1, files.length);
    return allFiles;
  }

  @Override
  public String[] getUsedFiles(boolean noPixels) {
    if (noPixels) {
      return new String[] {currentId};
    }
    String[] allFiles = new String[files.length + 1];
    allFiles[0] = currentId;
    System.arraycopy(files, 0, allFiles, 1, files.length);
    return allFiles;
  }

  @Override
  public void setMetadataFiltered(boolean filter) {
    helper.setMetadataFiltered(filter);
  }

  @Override
  public boolean isMetadataFiltered() { return helper.isMetadataFiltered(); }

  @Override
  public void setMetadataStore(MetadataStore store) {
    super.setMetadataStore(store);
    helper.setMetadataStore(store);
  }

  @Override
  public IFormatReader[] getUnderlyingReaders() {
    return new IFormatReader[] {helper};
  }

  @Override
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  @Override
  public String getDatasetStructureDescription() {
    return helper.getDatasetStructureDescription();
  }

  @Override
  public boolean hasCompanionFiles() {
    return true;
  }

  @Override
  public String[] getPossibleDomains(String id)
    throws FormatException, IOException
  {
    return helper.getPossibleDomains(id);
  }

  @Override
  public String[] getDomains() {
    return helper.getDomains();
  }

  @Override
  public boolean hasFlattenedResolutions() {
    return helper.hasFlattenedResolutions();
  }

  @Override
  public void setFlattenedResolutions(boolean flattened) {
    helper.setFlattenedResolutions(flattened);
  }

  /* @see loci.formats.IFormatReader#reopenFile() */
  @Override
  public void reopenFile() throws IOException {

    if (helper != null) {
      helper.close();
    }

    if (files != null) {
      IFormatReader r = new ImageReader(newClasses);
      helper = Memoizer.wrap(getMetadataOptions(), r);
      stitcher = null;
    }
    else if (helper == null) {
      stitcher = new FileStitcher(new ImageReader(newClasses));
      IFormatReader r = new ImageReader(newClasses);
      helper = Memoizer.wrap(getMetadataOptions(), r);
    }

    if (stitcher != null) {
      stitcher.setUsingPatternIds(true);
      stitcher.setCanChangePattern(false);
      try {
        helper.setId(pattern);
      }
      catch (FormatException e) {
        throw new IOException("Could not reopen file", e);
      }
    }
    else {
      try {
        helper.setId(files[0]);
      }
      catch (FormatException e) {
        throw new IOException("Could not reopen " + files[0], e);
      }
    }
  }

  // -- IFormatHandler API methods --

  @Override
  public Class<?> getNativeDataType() {
    return helper.getNativeDataType();
  }

  @Override
  public void close() throws IOException {
    if (helper != null) {
      helper.close();
    }
  }

  // -- Internal FormatReader methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  @Override
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    currentId = new Location(id).getAbsolutePath();

    Map<String, String> pairs = new HashMap<String, String>();
    BufferedReader br = new BufferedReader(new FileReader(id));
    String line;
    while ((line = br.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      if (line.charAt(0) == '#') {
        String[] kv = line.substring(1).split("\\s+=\\s+", 2);
        if (kv.length == 2) {
          pairs.put(kv[0].trim(), kv[1].trim());
        }
      } else {
        pattern = line;
      }
    }
    br.close();

    String excludeReadersEntry = pairs.get("ExcludeReaders");
    if (null != excludeReadersEntry) {
      String[] excludeReaders = excludeReadersEntry.split(",", -1);
      for (String r : excludeReaders) {
        try {
          Class<? extends IFormatReader> c =
            Class.forName(r).asSubclass(IFormatReader.class);
          newClasses.removeClass(c);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Reader not found: " + r);
        }
      }
      stitcher.setReaderClassList(newClasses);
    }

    String dir = new Location(id).getAbsoluteFile().getParent();
    if (new Location(pattern).getParent() == null) {
      pattern = dir + File.separator + pattern;
    }
    reopenFile();
    core.clear();
    for (CoreMetadata m : helper.getCoreMetadataList()) {
      core.add(new CoreMetadata(m));
    }

    files = stitcher.getUsedFiles();
    fileIndexes = new int[getSeriesCount()][][];
    for (int s=0; s<getSeriesCount(); s++) {
      setSeries(s);
      fileIndexes[s] = new int[core.get(s).imageCount][];
      for (int p=0; p<fileIndexes[s].length; p++) {
        fileIndexes[s][p] = stitcher.computeIndices(p);
      }
    }
    setSeries(0);

    MetadataStore store = makeFilterMetadata();

    String channelNamesEntry = pairs.get("ChannelNames");
    if (null != channelNamesEntry) {
      String[] channelNames = channelNamesEntry.split(",", -1);
      for (int s = 0; s < getSeriesCount(); s++) {
        for (int i = 0; i < channelNames.length; i++) {
          store.setChannelName(channelNames[i], s, i);
        }
      }
    }
  }

}

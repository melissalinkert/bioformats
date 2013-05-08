/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
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
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.formats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import loci.formats.in.MetadataOptions;

import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.filefilter.TrueFileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/DirectoryParser.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/DirectoryParser.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class DirectoryParser extends DirectoryWalker {

  // -- Constants --

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryParser.class);

  // -- Fields --

  protected IFormatReader reader;

  protected int depth;

  /** Total time spent on calling {@link IFormatReader#setId()} */
  protected long readerTime = 0;

  /** Number of calls to {@link IFormatReader#setId()} */
  protected int setids = 0;

  /** Number of files of an unknown format */
  protected int unknown = 0;

  /** Number of files processed so far */
  protected int count = 0;

  /** Total number of files */
  protected int total = 0;

  /** Whether or not directory scanning has been cancelled */
  protected boolean cancelled = false;

  /** List of filesets */
  protected List<FileInfo> filesets = new ArrayList<FileInfo>();

  private final Set<String> allFiles = new HashSet<String>();
  private final Map<String, List<String>> usedBy = new LinkedHashMap<String, List<String>>();

  private final long start = System.currentTimeMillis();

  // -- Constructors --

  public DirectoryParser() {
    this(new ImageReader(), 4, null);
  }

  public DirectoryParser(IFormatReader reader, int depth, MetadataOptions options) {
    super(TrueFileFilter.INSTANCE, depth);
    this.reader = reader;
    if (options != null) {
      this.reader.setMetadataOptions(options);
    }
    this.depth = depth;

    LOGGER.info("Depth: {} Metadata Level: {}", depth,
      reader.getMetadataOptions().getMetadataLevel());
  }

  // -- DirectoryParser API methods --

  public List<FileInfo> getFilesets(String[] paths) {
    if (paths == null || paths.length == 0) {
      LOGGER.info("No paths specified.");
      return null;
    }

    Groups g = null;
    try {
      execute(paths);
      total = count;
      count = 0;
      execute(paths);
      g = new Groups(usedBy);
      g.parse(filesets);

      long elapsed = System.currentTimeMillis() - start;
      LOGGER.info(String.format("%s file(s) parsed into %s group(s) with %s " +
        "call(s) to setId in %sms. (%sms total) [%s unknowns]", this.total,
        size(), this.setids, readerTime, elapsed, unknown));
    }
    catch (CANCEL c) {
      LOGGER.info(String.format("Cancelling search after %sms with %s " +
        "filesets found (%sms in %s calls to setId)",
        System.currentTimeMillis() - start, filesets.size(),
        readerTime, setids));
      filesets.clear();
      cancelled = true;
      g = null;
      total = -1;
      count = -1;
    }

    return filesets;
  }

  /**
   * @return whether or not directory parsing was cancelled
   */
  public boolean wasCancelled() {
    return cancelled;
  }

  /**
   * @return number of filesets
   */
  public int size() {
    return filesets.size();
  }

  /**
   * @return array of paths for all filesets
   */
  public List<String> getPaths() {
    List<String> paths = new ArrayList<String>();
    for (FileInfo info : filesets) {
      paths.add(info.filename);
    }
    return paths;
  }

  /** Return list of files used by the given path. */
  public String[] getUsedFiles(String path) {
    for (FileInfo info : filesets) {
      if (info.filename.equals(path)) {
        return info.usedFiles;
      }
    }
    throw new RuntimeException("Did not find used files for: " + path);
  }

  /**
   * Retrieve reader type for the given path.
   */
  public String getReaderType(String path) {
    for (FileInfo info : filesets) {
      if (info.filename.equals(path)) {
        return info.reader.getCanonicalName();
      }
    }
    throw new RuntimeException("Did not find reader for: " + path);
  }

  // -- DirectoryWalker API methods --

  @Override
  public void handleFile(File file, int depth, Collection collection) {
    count++;

    if (file.getName().startsWith(".")) {
      return; // Omitting dot files.
    }

    if (count % 100 == 0) {
      scanWithCancel(file, depth);
    }

    if (total < 0 || allFiles.contains(file.getAbsolutePath())) {
      return;
    }

    FileInfo info = singleFile(file);
    if (info == null) {
      return;
    }

    filesets.add(info);
    allFiles.addAll(Arrays.asList(info.usedFiles));
    for (String f : info.usedFiles) {
      List<String> users = usedBy.get(f);
      if (users == null) {
        users = new ArrayList<String>();
        usedBy.put(f, users);
      }
      users.add(file.getAbsolutePath());
    }
  }

  // -- API methods to override --

  protected void scanWithCancel(File f, int depth) throws CANCEL {

  }

  protected void safeUpdate(String path, Throwable t) {

  }

  // -- Non-API methods --

  protected void execute(String[] paths) {
    for (String path : paths) {
      try {
        File f = new File(path);
        if (f.isDirectory()) {
          walk(f, null);
        }
        else {
          handleFile(f, 0, null);
        }
        scanWithCancel(f, 0);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  protected FileInfo singleFile(File file) {
    if (file == null) {
      return null;
    }

    final String path = file.getAbsolutePath();
    if (!file.exists() || !file.canRead()) {
      LOGGER.info("File not readable: {}", path);
      return null;
    }

    long start = System.currentTimeMillis();

    try {
      try {
        setids++;
        reader.close();
        reader.setId(path);
        FileInfo info = new FileInfo();
        info.filename = path;
        info.usedFiles = getOrderedFiles();
        info.reader = reader.getClass();
        if (reader instanceof ReaderWrapper) {
          info.reader = ((ReaderWrapper) reader).unwrap().getClass();
        }
        info.isSPW = Arrays.asList(reader.getDomains()).contains(FormatTools.HCS_DOMAIN);
        return info;
      }
      finally {
        readerTime += (System.currentTimeMillis() - start);
        reader.close();
      }
    }
    catch (UnsupportedCompressionException e) {
      unknown++;
      safeUpdate(path, e);
    }
    catch (UnknownFormatException e) {
      unknown++;
      safeUpdate(path, e);
    }
    catch (Throwable t) {
      safeUpdate(path, t);
    }
    return null;
  }

  /**
   * Uses the {@link FileInfo#usedToInitialize} flag to re-order used files.
   * All files which can be used to initialize a fileset are returned first.
   */
  private String[] getOrderedFiles() {
    FileInfo[] infos = reader.getAdvancedUsedFiles(false);
    ArrayList<String> usedFiles = new ArrayList<String>();

    for (FileInfo info : infos) {
      if (info.usedToInitialize && usedFiles.size() > 0) {
        usedFiles.add(1, info.filename);
      }
      else {
        usedFiles.add(info.filename);
      }
    }

    return usedFiles.toArray(new String[usedFiles.size()]);
  }

  // -- Internal classes --

  /**
   * Marker exception raised if the directory scanning is cancelled.
   */
  public static class CANCEL extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  /**
   * The {@link Groups} class serves as an algorithm for sorting the usedBy
   * map from the {@link DirectoryParser#walk(File, Collection)} method.
   * These objects should never leave the outer class.
   *
   * It is important that the Groups keep their used files ordered.
   * @see DirectoryParser#getOrderedFiles()
   */
  private static class Groups {

    private class Group {
      String key;
      List<String> theyUseMe;
      List<String> iUseThem;

      public Group(String key) {
        this.key = key;
        this.theyUseMe = new ArrayList<String>(usedBy.get(key));
        this.theyUseMe.remove(key);
        this.iUseThem = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : usedBy.entrySet()) {
          if (entry.getValue().contains(key)) {
            iUseThem.add(entry.getKey());
          }
        }
        iUseThem.remove(key);
      }

      public void removeSelfIfSingular() {
        int users = theyUseMe.size();
        int used = iUseThem.size();
        if (used <= 1 && users > 0) {
          groups.remove(key);
        }
      }

      public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("\n");
        for (String val : iUseThem) {
          sb.append(val);
          sb.append("\n");
        }
        return sb.toString();
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("#======================================\n");
        sb.append("# Group: ");
        sb.append(key);
        sb.append("\n");
        sb.append(toShortString());
        return sb.toString();
      }
    }

    private final Map<String, List<String>> usedBy;
    private final Map<String, Group> groups = new LinkedHashMap<String, Group>();
    private List<String> ordering;

    Groups(Map<String, List<String>> usedBy) {
      this.usedBy = usedBy;
      for (String key : usedBy.keySet()) {
        groups.put(key, new Group(key));
      }
    }

    public int size() {
      return ordering.size();
    }

    Groups parse(List<FileInfo> infos) {
      if (ordering != null) {
        throw new RuntimeException("Already ordered");
      }

      for (Group g : new ArrayList<Group>(groups.values())) {
        g.removeSelfIfSingular();
      }

      ordering = new ArrayList<String>(groups.keySet());

      List<FileInfo> copy = new ArrayList<FileInfo>(infos);
      infos.clear();
      for (String key : ordering) {
        for (FileInfo info : copy) {
          if (info.filename.equals(key)) {
            infos.add(info);
          }
        }
      }

      for (FileInfo info : infos) {
        info.filename = info.usedFiles[0];
      }

      return this;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Group g : groups.values()) {
        sb.append(g.toString());
        sb.append("\n");
      }
      return sb.toString();
    }


  }

}

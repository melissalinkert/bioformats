package ome.scifio;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Set;

import ome.scifio.MetadataLevel;
import ome.scifio.MetadataOptions;
import ome.scifio.io.RandomAccessInputStream;

/**
 * Interface for all SciFIO Parsers.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="">Trac</a>,
 * <a href="">Gitweb</a></dd></dl>
 */
public interface Parser<M extends Metadata> extends MetadataHandler<M> {

  // -- Parser API methods --

  /**
   * Wraps the file corresponding to the given name in a File handle and returns parse(RandomAccessInputStream).
   * 
   * @param fileName Path to an image file to be parsed.  Parsers are typically
   *   specific to the file type discovered here.
   * @return most specific metadata for this type
   * @throws IOException 
   */

  M parse(String fileName) throws IOException, FormatException;

  /**
   * Wraps the file in a File handle and returns parse(RandomAccessInputStream).
   * 
   * @param file Path to an image file to be parsed.  Parsers are typically
   *   specific to the file type discovered here.
   * @return most specific metadata for this type
   * @throws IOException 
   */
  M parse(File file) throws IOException, FormatException;

  /**
   * Returns the most specific Metadata object possible, for the provided RandomAccessInputStream.
   * 
   * @param stream random access handle to the file to be parsed.
   * @return most specific metadata for this type   
   * @throws IOException 
   */
  M parse(RandomAccessInputStream stream) throws IOException, FormatException;

  /**
   * Closes the currently open file. If the flag is set, this is all that
   * happens; if unset, it is equivalent to calling
   */
  void close(boolean fileOnly) throws IOException;

  /** Closes currently open file(s) and frees allocated memory. */
  public void close() throws IOException;

  /** Gets the number of series in this file. */
  //TODO int getSeriesCount();

  /** Activates the specified series. */
  //TODO void setSeries(int no);

  /** Gets the currently active series. */
  //TODO int getSeries();

  /**
   * Specifies whether or not to save proprietary metadata
   * in the Metadata.
   */
  void setOriginalMetadataPopulated(boolean populate);

  /**
   * Returns true if we should save proprietary metadata
   * in the Metadata.
   */
  boolean isOriginalMetadataPopulated();

  /** Returns an array of filenames needed to open this dataset. */
  String[] getUsedFiles();

  /**
   * Returns an array of filenames needed to open this dataset.
   * If the 'noPixels' flag is set, then only files that do not contain
   * pixel data will be returned.
   */
  String[] getUsedFiles(boolean noPixels);

  /**
   * Specifies whether ugly metadata (entries with unprintable characters,
   * and extremely large entries) should be discarded from the metadata table.
   */
  void setMetadataFiltered(boolean filter);

  /**
   * Returns true if ugly metadata (entries with unprintable characters,
   * and extremely large entries) are discarded from the metadata table.
   */
  boolean isMetadataFiltered();

  /** Returns an array of filenames needed to open the indicated image index. */
  String[] getImageUsedFiles(int iNo);

  /**
   * Returns an array of filenames needed to open the indicated image.
   * If the 'noPixels' flag is set, then only files that do not contain
   * pixel data will be returned.
   */
  String[] getImageUsedFiles(int iNo, boolean noPixels);

  /**
   * Returns an array of FileInfo objects representing the files needed
   * to open this dataset.
   * If the 'noPixels' flag is set, then only files that do not contain
   * pixel data will be returned.
   */
  FileInfo[] getAdvancedUsedFiles(boolean noPixels);

  /**
   * Returns an array of FileInfo objects representing the files needed to
   * open the current series.
   * If the 'noPixels' flag is set, then only files that do not contain
   * pixel data will be returned.
   */
  FileInfo[] getAdvancedImageUsedFiles(int iNo, boolean noPixels);

  /** Adds an entry to the specified Hashtable */
  void addMeta(String key, Object value, Hashtable<String, Object> meta);
  
  /** Returns a list of MetadataLevel options for determining the granularity of MetadataCollection */
  Set<MetadataLevel> getSupportedMetadataLevels();

  /** Sets the MetadataOptions of this Parser */
  void setMetadataOptions(MetadataOptions options);

  /** Returns the MetadataOptions for this Parser */
  MetadataOptions getMetadataOptions();
}
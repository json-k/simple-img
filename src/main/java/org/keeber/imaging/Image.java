package org.keeber.imaging;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Arrays;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.options.SerializeOptions;
import com.github.jaiimageio.plugins.tiff.BaselineTIFFTagSet;
import com.github.jaiimageio.plugins.tiff.TIFFDirectory;
import com.github.jaiimageio.plugins.tiff.TIFFField;
import com.github.jaiimageio.plugins.tiff.TIFFImageWriteParam;
import com.github.jaiimageio.plugins.tiff.TIFFTag;

public class Image {
  private BufferedImage raster;
  private Image.Type type;
  private XMPMeta xmp;
  private int res = 0;
  private Color background = Color.WHITE;
  private ICC_Profile profile = null;

  private Image(BufferedImage raster, IIOMetadata metadata, Type type) {
    this.raster = raster;
    this.type = type;
    init(metadata);
  }


  public ICC_Profile getProfile() {
    return profile;
  }

  public Image setProfile(ICC_Profile profile) {
    this.profile = profile;
    return this;
  }

  public Image setProfile(byte[] profile) {
    this.profile = ICC_Profile.getInstance(profile);
    return this;
  }

  /**
   * Creates a copy of this image that can be manipulated without changing the original.
   */
  public Image clone() {
    return new Image(this.raster.getSubimage(0, 0, this.raster.getWidth(), this.raster.getHeight()), null, this.type).setXMP(xmp).setRes(this.res).setBackground(this.background);
  }

  /**
   * Returns the Adobe XMP Object from the initial image read (if any) - unless the
   * {@see #clearXMP()} method has been called.
   * 
   * @return Adobe XMP Object
   */
  public XMPMeta getXMP() {
    return xmp;
  }

  public Image setXMP(XMPMeta xmp) {
    this.xmp = xmp;
    return this;
  }

  /**
   * Clears the Adobe XMP object and returns a reference for further modification. This avoids
   * overwriting image information (such as compression etc).
   * 
   * @return Adobe XMP Object
   */
  public XMPMeta clearXMP() {
    xmp = XMPMetaFactory.create();
    return xmp;
  }

  /**
   * Serializes the Adobe XMP Object to a String using the factory method with no Serialize Options.
   * 
   * @return a serialization of the Adobe XMP Object
   */
  public String printXMP() {
    try {
      return XMPMetaFactory.serializeToString(xmp, null);
    } catch (XMPException e) {
      return "Error serializing XMP [" + e.getLocalizedMessage() + "].";
    }
  }

  /**
   * Remove the alpha channel from the image.
   * 
   * @return
   */
  public Image removeAlpha() {
    return normalize(true);
  }

  /**
   * Convert this image to an INT_ARGB or INT_RGB BufferedImage Type.
   * 
   * @param removeAlpha to optionally replace the alpha channel with the background color
   * @return
   */
  public Image normalize(boolean removeAlpha) {
    BufferedImage tmp = new BufferedImage(raster.getWidth(), raster.getHeight(), removeAlpha || !raster.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = tmp.createGraphics();
    g.drawImage(raster, 0, 0, removeAlpha || !raster.getColorModel().hasAlpha() ? background : Constants.TRANSPARENT, null);
    g.dispose();
    this.raster = tmp;
    return this;
  }

  /**
   * Edits and image
   * 
   * @param edits
   * @return
   */
  private Image edit(Image.EditParams edits) {
    BufferedImage tmp = new BufferedImage(edits.canvasWidth, edits.canvasHeight, edits.flatten || !raster.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = tmp.createGraphics();
    g.setBackground(background);
    if (edits.flatten || tmp.getType() == BufferedImage.TYPE_INT_RGB) {
      g.clearRect(0, 0, tmp.getWidth(), tmp.getHeight());
    }
    g.drawImage(raster.getScaledInstance(edits.imageWidth, edits.imageHeight, java.awt.Image.SCALE_SMOOTH), edits.offsetX, edits.offsetY, edits.imageWidth, edits.imageHeight, null);
    g.dispose();
    this.raster = tmp;
    return this;
  }

  /**
   * Used when flattening (ie: converting an image with alpha to one without) to determine the
   * background color.
   * 
   * @return
   */
  public Color getBackground() {
    return background;
  }

  /**
   * Used when flattening (ie: converting an image with alpha to one without) to determine the
   * background color.
   * 
   * @param background
   * @return
   */
  public Image setBackground(Color background) {
    this.background = background;
    return this;
  }

  /**
   * The currently set resolution - or the one read when the image was created (defaults to 72).
   * Always in DPI.
   * 
   * @return
   */
  public int getRes() {
    return res;
  }

  /**
   * The DPI resolution to write into the image.
   * 
   * @param res in DPI
   * @return
   */
  public Image setRes(int res) {
    this.res = res;
    return this;
  }

  private static class Constants {
    public static Color TRANSPARENT = new Color(0x00ffffff, true);
    public static final String XMP_PACKET_START = "<?xpacket begin";
    public static final int ICC_HEADER_SIZE = 14;
    private static int XMP_HEADER_SIZE = 29;
    private static SerializeOptions SERIALIZE_OPTIONS = new SerializeOptions().setUseCompactFormat(true);
    private static BaselineTIFFTagSet BASE = BaselineTIFFTagSet.getInstance();
    private static String PNG_KEYWORD = "XML:com.adobe.xmp";
  }

  /**
   * Broke out here in case we add a second constructor;
   */
  private void init(IIOMetadata metadata) {
    String xmpData = null;
    if (type == Image.Type.JPG) {
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(type.id);
      IIOMetadataNode markerSequence = (IIOMetadataNode) root.getElementsByTagName("markerSequence").item(0);
      NodeList nodes = markerSequence.getElementsByTagName("unknown");
      for (int i = 0; i < nodes.getLength(); i++) {
        if (((IIOMetadataNode) nodes.item(i)).getAttribute("MarkerTag").matches("APP1|225")) {
          byte[] b = (byte[]) ((IIOMetadataNode) nodes.item(i)).getUserObject();
          xmpData = new String(b, Constants.XMP_HEADER_SIZE, b.length - Constants.XMP_HEADER_SIZE);
        }
        if (((IIOMetadataNode) nodes.item(i)).getAttribute("MarkerTag").matches("APP2|226")) {
          byte[] b = (byte[]) ((IIOMetadataNode) nodes.item(i)).getUserObject();
          b = Arrays.copyOfRange(b, Constants.ICC_HEADER_SIZE, b.length);
          profile = ICC_Profile.getInstance(b);
        }
      }
    }
    if (type == Image.Type.PNG) {
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(type.id);
      NodeList nodes = root.getElementsByTagName("iTXtEntry");
      for (int i = 0; i < nodes.getLength(); i++) {
        if (((IIOMetadataNode) nodes.item(i)).getAttribute("keyword").equals(Constants.PNG_KEYWORD)) {
          xmpData = ((IIOMetadataNode) nodes.item(i)).getAttribute("text");
        }
      }
      nodes = root.getElementsByTagName("pHYs");
      if (nodes.getLength() == 1) {
        IIOMetadataNode r = (IIOMetadataNode) nodes.item(0);
        if (r.getAttribute("unitSpecifier").equals("meter")) {
          res = Math.round(Integer.parseInt(r.getAttribute("pixelsPerUnitXAxis")) * 0.0254f);
        }
      }
      if (res == 0) {
        root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_1.0");
        nodes = root.getElementsByTagName("HorizontalPixelSize");
        if (nodes.getLength() == 1) {
          IIOMetadataNode r = (IIOMetadataNode) nodes.item(0);
          res = (int) Math.round(25.4 * Float.parseFloat(r.getAttribute("value")));
        }
      }
    }
    if (type == Image.Type.TIF || type == Image.Type.JPG) {
      try {
        TIFFDirectory t = TIFFDirectory.createFromMetadata(metadata);
        if (t.containsTIFFField(700)) {
          xmpData = new String(t.getTIFFField(700).getAsBytes());
        }
        // Resolution
        if (t.containsTIFFField(BaselineTIFFTagSet.TAG_X_RESOLUTION)) {
          long[] r = t.getTIFFField(BaselineTIFFTagSet.TAG_X_RESOLUTION).getAsRational(0);
          res = Math.floorDiv((int) r[0], (int) r[1]);
          if (t.containsTIFFField(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT) && t.getTIFFField(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT).getAsChars()[0] == 3) {
            res = Math.round(res * 2.54f);
          }
        }
        // ICC Profile
        if (t.containsTIFFField(BaselineTIFFTagSet.TAG_ICC_PROFILE)) {
          profile = ICC_Profile.getInstance(t.getTIFFField(BaselineTIFFTagSet.TAG_ICC_PROFILE).getAsBytes());
        }
      } catch (IIOInvalidTreeException e) {

      }
    }
    if (xmpData != null) {
      if (xmpData.startsWith(Constants.XMP_PACKET_START)) {
        try {
          this.xmp = XMPMetaFactory.parseFromString(xmpData);
        } catch (XMPException e) {

        }
      } else {

      }
    }
    this.xmp = xmp == null ? XMPMetaFactory.create() : xmp;
    this.res = res == 0 ? 72 : res;
  }

  /**
   * An image type definition. Holds the extension, the reader type and the metadata namespace.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public enum Type {
    TIF("tif", "com_sun_media_imageio_plugins_tiff_image_1.0", "tif"), JPG("jpg", "javax_imageio_jpeg_image_1.0", "jpg"), PNG("png", "javax_imageio_png_1.0", "png");

    private String fm, id, ex;

    private Type(String format, String id, String ext) {
      this.fm = format;
      this.id = id;
      this.ex = ext;
    }

    public String getExtention() {
      return ex;
    }

  }

  public edit edit = new edit();

  public class edit {

    public Image longestSide(int length) {
      return Image.this.edit(new EditParams().longestSide(length));
    }

    public Image place(int canvasWidth, int canvasHeight) {
      return Image.this.edit(new EditParams().place(canvasWidth, canvasHeight));
    }

  }

  public write write = new write();

  /**
   * Holder for the write methods.
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public class write {

    public void to(OutputStream os, Image.Type otype) throws IOException {
      write.to(os, otype, xmp, false);
    }

    public void to(OutputStream os, Image.Type otype, boolean flatten) throws IOException {
      write.to(os, otype, xmp, flatten);
    }

    public void to(OutputStream os, Image.Type oType, XMPMeta oXmp, boolean flatten) throws IOException {
      ImageWriter writer = ImageIO.getImageWritersByFormatName(oType.fm).next();
      BufferedImage oRaster = raster;
      if (oRaster.getColorModel().hasAlpha() && (flatten || oType == Image.Type.JPG)) {
        oRaster = new BufferedImage(raster.getWidth(), raster.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = oRaster.createGraphics();
        g.drawImage(raster, 0, 0, background, null);
        g.dispose();
      }
      ImageWriteParam p = writer.getDefaultWriteParam();
      IIOMetadata m = writer.getDefaultImageMetadata(new ImageTypeSpecifier(oRaster), null);
      // Serialize the XMP
      byte[] xmpBytes, profileBytes;
      try {
        xmpBytes = XMPMetaFactory.serializeToBuffer(oXmp, Constants.SERIALIZE_OPTIONS);
      } catch (XMPException e) {
        throw new IOException("Failed to serialize XMP[" + e.getLocalizedMessage() + "].", e);
      }
      profileBytes = profile == null ? new byte[0] : profile.getData();
      if (oType == Image.Type.JPG) {
        IIOMetadataNode root = (IIOMetadataNode) m.getAsTree(oType.id);
        IIOMetadataNode markerSequence = (IIOMetadataNode) root.getElementsByTagName("markerSequence").item(0);
        // XMP
        {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          bos.write("http://ns.adobe.com/xap/1.0/\0".getBytes());
          bos.write(xmpBytes);
          IIOMetadataNode exif = new IIOMetadataNode("unknown");
          exif.setAttribute("MarkerTag", String.valueOf(0xE1));
          exif.setUserObject(bos.toByteArray());
          markerSequence.appendChild(exif);
        }
        // ICC
        if (profileBytes.length > 0) {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          bos.write("ICC_PROFILE\0".getBytes());
          bos.write("\1\1".getBytes()); // Chunk count
          bos.write(profileBytes);
          IIOMetadataNode icc = new IIOMetadataNode("unknown");
          icc.setAttribute("MarkerTag", String.valueOf(0xE2));
          icc.setUserObject(bos.toByteArray());
          markerSequence.appendChild(icc);
        }
        //
        {
          NodeList ch = root.getElementsByTagName("JPEGvariety");
          IIOMetadataNode jpegVariety;
          if (ch.getLength() > 0) {
            jpegVariety = (IIOMetadataNode) ch.item(0);
          } else {
            root.appendChild(jpegVariety = new IIOMetadataNode("JPEGvariety"));
          }
          ch = jpegVariety.getElementsByTagName("app0JFIF");
          IIOMetadataNode app0JFIF;
          if (ch.getLength() > 0) {
            app0JFIF = (IIOMetadataNode) ch.item(0);
          } else {
            jpegVariety.appendChild(app0JFIF = new IIOMetadataNode("app0JFIF"));
          }
          app0JFIF.setAttribute("majorVersion", "1");
          app0JFIF.setAttribute("minorVersion", "2");
          app0JFIF.setAttribute("thumbWidth", "0");
          app0JFIF.setAttribute("thumbHeight", "0");
          app0JFIF.setAttribute("resUnits", "1");
          app0JFIF.setAttribute("Xdensity", String.valueOf(res));
          app0JFIF.setAttribute("Ydensity", String.valueOf(res));
        }
        m.setFromTree(oType.id, root);
      }
      if (oType == Image.Type.TIF) {
        TIFFDirectory t = TIFFDirectory.createFromMetadata(m);
        // XMP
        t.addTIFFField(new TIFFField(new TIFFTag("xmp", 700, TIFFTag.TIFF_BYTE), TIFFTag.TIFF_BYTE, xmpBytes.length, xmpBytes));
        // RES
        t.addTIFFField(new TIFFField(Constants.BASE.getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1, new long[][] {{res, 1}}));
        t.addTIFFField(new TIFFField(Constants.BASE.getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION), TIFFTag.TIFF_RATIONAL, 1, new long[][] {{res, 1}}));
        t.addTIFFField(new TIFFField(Constants.BASE.getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT), TIFFTag.TIFF_SHORT, 1, new char[] {2}));
        // ICC PROFILE
        if (profileBytes.length > 0) {
          t.addTIFFField(new TIFFField(Constants.BASE.getTag(BaselineTIFFTagSet.TAG_ICC_PROFILE), TIFFTag.TIFF_BYTE, profileBytes.length, profileBytes));
        }
        //
        m = t.getAsMetadata();
        // PARAMS
        TIFFImageWriteParam tp = (TIFFImageWriteParam) p;
        tp.setCompressionMode(TIFFImageWriteParam.MODE_EXPLICIT);
        tp.setCompressionType("LZW");
      }
      if (oType == Image.Type.PNG) {
        IIOMetadataNode root = (IIOMetadataNode) m.getAsTree(oType.id);

        IIOMetadataNode t = new IIOMetadataNode("iTXt");
        root.appendChild(t);
        IIOMetadataNode x = new IIOMetadataNode("iTXtEntry");
        t.appendChild(x);
        x.setAttribute("keyword", Constants.PNG_KEYWORD);
        x.setAttribute("compressionMethod", "0");
        x.setAttribute("compressionFlag", "FALSE");
        x.setAttribute("languageTag", "");
        x.setAttribute("translatedKeyword", "");
        x.setAttribute("text", new String(xmpBytes));
        m.mergeTree(oType.id, root);
        // DPI (We hope).
        root = (IIOMetadataNode) m.getAsTree("javax_imageio_1.0");
        IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
        horiz.setAttribute("value", Double.toString(res / 25.4));
        IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
        vert.setAttribute("value", Double.toString(res / 25.4));
        IIOMetadataNode dim = new IIOMetadataNode("Dimension");
        dim.appendChild(horiz);
        dim.appendChild(vert);
        root.appendChild(dim);
        // ICC Profile
        root = (IIOMetadataNode) m.getAsTree("javax_imageio_1.0");
        IIOMetadataNode iccp = new IIOMetadataNode("iCCP");
        iccp.setUserObject(profileBytes);

        root.appendChild(iccp);


        m.mergeTree("javax_imageio_1.0", root);
      }
      File tmp = File.createTempFile("JImage", "." + oType.getExtention());
      tmp.deleteOnExit();
      writer.setOutput(ImageIO.createImageOutputStream(tmp));
      writer.write(null, new IIOImage(oRaster, null, m), p);
      writer.dispose();
      copy(new FileInputStream(tmp), os);
      tmp.delete();
    }

    private void copy(InputStream is, OutputStream os) throws IOException {
      byte[] buffer = new byte[1024 * 8];
      int len;
      while ((len = is.read(buffer)) > 0) {
        os.write(buffer, 0, len);
      }
      os.flush();
      is.close();
      os.close();
    }

  }


  private class EditParams {
    private int canvasWidth, canvasHeight, offsetX, offsetY, imageWidth, imageHeight;
    private boolean flatten = false;

    public EditParams longestSide(int length) {
      offsetX = 0;
      offsetY = 0;
      float scale = (1f * length) / Math.max(raster.getWidth(), raster.getHeight());
      canvasWidth = imageWidth = Math.round(scale * raster.getWidth());
      canvasHeight = imageHeight = Math.round(scale * raster.getHeight());
      return this;
    }

    public EditParams place(int canvasWidth, int canvasHeight) {
      this.canvasWidth = canvasWidth;
      this.canvasHeight = canvasHeight;
      float scale = Math.min((1f * canvasWidth) / raster.getWidth(), (1f * canvasHeight) / raster.getHeight());
      imageWidth = Math.round(scale * raster.getWidth());
      imageHeight = Math.round(scale * raster.getHeight());
      offsetX = (canvasWidth - imageWidth) / 2;
      offsetY = (canvasHeight - imageHeight) / 2;
      return this;
    }


  }


  /**
   * Holder for the read method(s).
   * 
   * @author Jason Keeber <jason@keeber.org>
   *
   */
  public static class read {

    public static Image from(InputStream is, Image.Type type) throws IOException {
      ImageReader reader = ImageIO.getImageReadersByFormatName(type.fm).next();
      reader.setInput(ImageIO.createImageInputStream(is), true, false);
      return new Image(reader.read(0), reader.getImageMetadata(0), type);
    }

  }

  public static class metadata {

    /**
     * This is useful for debugging all kinds of XML data.
     * 
     * @param node
     * @return XML converted to String
     */
    public static String nodeToString(Node node) {
      StringWriter sw = new StringWriter();
      try {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(node), new StreamResult(sw));
      } catch (TransformerException te) {
        System.out.println("nodeToString Transformer Exception");
      }
      return sw.toString();
    }

    /**
     * Delegates to the {see {@link XMPMetaFactory#create()} method.
     * 
     * @return
     */
    public static XMPMeta newXMPMeta() {
      return XMPMetaFactory.create();
    }

    /**
     * This is a delegate for the {@see XMPMetaFactory#getSchemaRegistry()#registerNS(String,
     * String)} method to allow access to add namespaces.
     * 
     * @param namespaceURI eg: "http://ogr.keeber.namespace/test/"
     * @param suggestedPrefix eg: tst
     * @throws XMPException
     */
    public static void registerNS(String namespaceURI, String suggestedPrefix) throws XMPException {
      XMPMetaFactory.getSchemaRegistry().registerNamespace(namespaceURI, suggestedPrefix);
    }


  }

}

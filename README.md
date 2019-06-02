# Simple Image

**Simple image** is a Java image read / write library.

# Philosophy

Reading and writing images should be easy right? Oh, and I need to write the resolution, some XMP etc... BTW I also need to read the custom XMP, you know, like PhotoShop right?

This happens a lot in the Premedia  / Studio Imaging industries and for some reason we always assume it will be simple, but in fact, it really isn't. This library wraps some of the java ImageIO and JAI calls and functions into something that can be implemented easily in production.

# Maven

The project is available **SOON** in the Maven Central Repository. For your Gradle build:

```
	compile 'org.keeber:simple-image:+'
```

# Quickstart

Read an image from an inputstream:

```java
Image img = Image.read.from(new FileInputStream("src/test/resources/images/test.tif")), Image.Type.TIF);
```

Set the resolution of the image - for later writing to the image:

```java
img.setResolution(300);
```

Clear the XMP data from the incoming image. Then add our own custom namespace and add a value to it:

```java
XMPMeta m = img.clearXMP();
Image.metadata.registerNS("http://namespace.keeber.org/test", "tst");
m.setProperty("http://namespace.keeber.org/test", "property1", "value1");
//Set some common properties: such as the rating.
m.setProperty(XMPConst.NS_DC, "title", "This is the title.");
m.setProperty(XMPConst.NS_XMP, "Rating", "5");
```

Save the image as a new type to an output stream:

```java
img.write.to(new FileOutputStream("build/images/test_out.jpg"), Image.Type.JPG);
```

Do some edits to the raster - here we place the image on a 500px square new image:

```java
img.normalize(false).edit.place(500, 500);
```

# TO-DO

 1. Figure out a prettier way of chaining and doing the edits.
 2. Support for ICC color profiles.
 3. Support for reading and writing CMYK images.
 
package IR;

import java.util.Set;

public class AnnotationDescriptor extends Descriptor {

  public static final int MARKER_ANNOTATION = 1;
  public static final int SINGLE_ANNOTATION = 2;
  public static final int FULL_ANNOTATION = 3;

  private String marker;
  private String value; // for single annotation
  private int type;

  public AnnotationDescriptor(String annotationName) {
    // constructor for marker annotation
    super(annotationName);
    this.marker = annotationName;
    this.type = MARKER_ANNOTATION;
  }

  public AnnotationDescriptor(String annotationName, String value) {
    // constructor for marker annotation
    super(annotationName);
    this.marker = annotationName;
    this.type = SINGLE_ANNOTATION;
    this.value = value;
  }

  public int getType() {
    return type;
  }

  public boolean isMarkerAnnotation() {
    return type == MARKER_ANNOTATION;
  }

  public boolean isSingleAnnotation() {
    return type == SINGLE_ANNOTATION;
  }

  public boolean isFullAnnotation() {
    return type == FULL_ANNOTATION;
  }

  public String getMarker() {
    return marker;
  }

  public String getValue() {
    return value;
  }

  public boolean equals(Object o) {
    if (o instanceof AnnotationDescriptor) {
      AnnotationDescriptor a = (AnnotationDescriptor) o;
      if (a.getType() != type)
        return false;
      if (!a.getMarker().equals(getMarker()))
        return false;

      return true;
    }
    return false;
  }

  public String toString() {
    if (type == MARKER_ANNOTATION) {
      return "@" + name;
    } else {
      return "@" + name + "(\""+getValue()+"\")";
    }
  }

}

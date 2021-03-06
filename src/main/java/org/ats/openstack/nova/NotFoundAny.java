package org.ats.openstack.nova;

import org.ats.openstack.exception.ExeceptionMessage;

public class NotFoundAny extends NovaException{

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private String message = null;
  
  public NotFoundAny() {
     this.message = ExeceptionMessage.NotFoundAnyNova;
  }

  public NotFoundAny(String message) {
      super(message);
      this.message = message;
  }

  public NotFoundAny(Throwable cause) {
      super(cause);
  }

  @Override
  public String toString() {
      return message;
  }

  @Override
  public String getMessage() {
      return message;
  }



}

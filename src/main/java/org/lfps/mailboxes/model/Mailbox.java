package org.lfps.mailboxes.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Mailbox {

  private final int id;
  private final String firstName;
  private final String lastName;
  private final String businessTitle;
  private final String boxNumber;
  private final String boxName;
  private final String phone;
  private final String email;
  private final List<String> alternateBusinessNames;

  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String boxName, String phone, String email,
      List<String> alternateBusinessNames) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.businessTitle = businessTitle;
    this.boxNumber = boxNumber;
    this.boxName = boxName;
    this.phone = phone;
    this.email = email;
    this.alternateBusinessNames = alternateBusinessNames == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(alternateBusinessNames));
  }

  public int getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getBusinessTitle() {
    return businessTitle;
  }

  public String getBoxNumber() {
    return boxNumber;
  }

  public String getBoxName() {
    return boxName;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public List<String> getAlternateBusinessNames() {
    return alternateBusinessNames;
  }

}

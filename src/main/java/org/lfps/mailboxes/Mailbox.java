package org.lfps.mailboxes;

public final class Mailbox {

  private final int id;
  private final String firstName;
  private final String lastName;
  private final String businessTitle;
  private final String boxNumber;
  private final String phone;
  private final String email;

  public Mailbox(int id, String firstName, String lastName, String businessTitle,
      String boxNumber, String phone, String email) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.businessTitle = businessTitle;
    this.boxNumber = boxNumber;
    this.phone = phone;
    this.email = email;
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

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

}

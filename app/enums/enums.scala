package enums

object ActionStates extends Enumeration {
  // At the main menu.
  val menu = Value
  // Clicked schedule button
  val schedule = Value
  // Has been prompted to select a day but has not yet
  val day = Value
  // Has been prompted to select a time but has not yet
  val time = Value
  // Has finished scheduling but is not at the menu
  val complete = Value
}

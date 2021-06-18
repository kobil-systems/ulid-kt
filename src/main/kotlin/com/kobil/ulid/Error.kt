package com.kobil.ulid

sealed class Error() {
  object NonBase32Char : Error()
  object TimeOutOfBounds : Error()
  object BytesOutOfBounds : Error()
  object IncorrectStringLength : Error()
}

package reactive.web.javascript

import JsTypes._

/**
 * Defines the interface of the browser's window object.
 * See `window` in the package object -- you should use
 * that instance (you can't instantiate it directly).
 */
sealed trait Window extends JsStub {
  def alert(s: $[JsString]): $[JsVoid]
  def encodeURIComponent(in: $[JsString]): $[JsString]

  def onbeforeunload: Assignable[JsObj =|> JsAny]
  def event: Assignable[JsObj]

  // TODO allow to use object syntax
  trait Location extends JsStub {
    def href: Assignable[JsString]
  }
  var location: Location

  trait HTMLElement extends JsStub {
    def focus(): $[JsVoid]
  }
  trait HTMLDocument extends JsStub {
    def getElementById(id: $[JsString]): HTMLElement
  }
  var document: HTMLDocument

  trait Console extends JsStub {
    def warning(msg: $[JsString]): $[JsVoid]
  }
  var console: Console

  trait JSON extends JsStub {
    def stringify(v: $[JsAny]): $[JsString]
  }
  var JSON: JSON

  def setTimeout(fn: JsExp[JsTypes.JsVoid =|> JsTypes.JsAny], timeout: JsExp[JsTypes.JsNumber]): JsExp[JsTypes.JsNumber]

  def confirm(message: JsExp[JsString]): JsExp[JsBoolean]

  def prompt(message: JsExp[JsString], default: JsExp[JsString] = ""): JsExp[JsString]

  def open(url: JsExp[JsString], target: JsExp[JsString], params: JsExp[JsString]): JsExp[JsObj]
}

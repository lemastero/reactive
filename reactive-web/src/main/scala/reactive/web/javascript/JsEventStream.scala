package reactive
package web
package javascript

import net.liftweb.json.{ Formats, DefaultFormats }

import JsTypes._

object JsEventStream {
  implicit def canForward[T, J <: JsAny](implicit conv: ToJs.From[T]#To[J, JsExp]): CanForward[JsEventStream[J], T] = new CanForward[JsEventStream[J], T] {
    def forward(source: Forwardable[T], target: => JsEventStream[J])(implicit o: Observing) =
      source foreach { v => target.fire(conv(v)) }
  }
}
//TODO use PageIds
class JsEventStream[T <: JsAny]()(implicit page: Page) extends JsExp[JsObj] with JsForwardable[T] { parent =>
  lazy val id = JsIdent.counter.next
  private var initialized = false
  def initExp = "new EventStream()"
  def render = "reactive.eventStreams["+id+"]"
  def init: Unit = synchronized {
    if (!initialized) {
      initialized = true
      Reactions queue render+"="+initExp
    }
  }

  protected def child[U <: JsAny](renderer: => String) = {
    init
    new JsEventStream[U]()(page) {
      override def initExp: String = renderer
    }
  }
  def fireExp: $[T=|>JsVoid] = {
    init
    JsRaw(render+".fire")
  }
  def fire(v: JsExp[T]) {
    Reactions queue fireExp(v).render
    Reactions queue "window.setTimeout('reactive.doAjax()',500)"
  }

  protected[reactive] def foreachImpl(f: $[T =|> JsVoid]) {
    init
    Reactions queue render+".foreach("+f.render+")"
  }
  def foreach[E[J <: JsAny] <: JsExp[J], F: ToJs.To[JsFunction1[T, JsVoid], E]#From](f: F) {
    foreachImpl(f)
  }
  def foreach(f: $[T =|> JsVoid]) {
    foreachImpl(f)
  }
  def toServer[U](extract: net.liftweb.json.JValue => U): EventStream[U] = {
    foreach(JsRaw[T =|> JsVoid]("reactive.queueAjax("+id+")"))
    page.ajaxEvents.collect { case (_id, json) if _id == id.toString => extract(json) }
  }
  def toServer[U](implicit formats: Formats = DefaultFormats, manifest: Manifest[U]): EventStream[U] =
    toServer(_.extract(formats, manifest))

  def map[U <: JsAny, F : ToJs.To[JsFunction1[T, U],JsExp]#From](f: F): JsEventStream[U] = child(parent.render+".map("+f.render+")")
//  def map[U<:JsAny](f: $[T=|>U]): JsEventStream[U] = child(parent.render+".map("+f.render+")")
  def flatMap[U <: JsAny, F <% JsExp[JsFunction1[T, U]]](f: F): JsEventStream[U] = child(parent.render+".flatMap("+f.render+")")
  def filter[F <% JsExp[JsFunction1[T, JsBoolean]]](f: F): JsEventStream[T] = child(parent.render+".filter("+f.render+")")
  //  def takeWhile(p: T=>Boolean): EventStream[T]
  //  def foldLeft[U](initial: U)(f: (U,T)=>U): EventStream[U]
  //  def |[U>:T](that: EventStream[U]): EventStream[U]
  //  def hold[U>:T](init: U): Signal[U]
  //  
  //  def nonrecursive: EventStream[T]

}
trait CanForwardJs[-T, V<:JsAny] {
  def forward(s: JsForwardable[V],t: T)
}
object CanForwardJs {
  implicit def jes[V<:JsAny] = new CanForwardJs[JsEventStream[V],V] {
    def forward(s: JsForwardable[V],t: JsEventStream[V]) =
      s.foreach((x:$[V]) => t.fireExp(x))(ToJs.func1)
  }
}
trait JsForwardable[T <: JsAny] {
  def foreach[E[J <: JsAny] <: JsExp[J], F: ToJs.To[JsFunction1[T, JsVoid], E]#From](f: F)
  def foreach(f: $[T =|> JsVoid])
  def ~>>[S](target: => S)(implicit canForward: CanForwardJs[S, T]): this.type = {
    canForward.forward(this, target)
    this
  }
}
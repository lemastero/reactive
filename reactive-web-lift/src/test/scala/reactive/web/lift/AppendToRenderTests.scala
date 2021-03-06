package reactive
package web
package lift

import org.scalatest.{FunSuite, Matchers}


class AppendToRenderTests extends FunSuite with Matchers {
  test("Render data correctly") {
    object page extends Page {
      val atrtt = new AppendToRenderTransportType(this) {
        override lazy val appendToRender = new AppendToRender {
          override lazy val currentPageRender = Some(new RenderTransport)
        }
      }
      val transportTypes = atrtt :: Nil
    }
    val s = "console.log('')"
    page queue StringRenderable(s)
    page.atrtt.appendToRender.currentPageRender.get.renderData should equal (s)
  }
}

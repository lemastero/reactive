package reactive

object Signal {
}

trait Signal[+T] {
  def now: T
  def change: EventStream[T]
  def foreach(f: T=>Unit)(implicit observing: Observing): Unit = change.foreach(f)(observing)
  def map[U, S](f: T=>U)(implicit canMapSignal: CanMapSignal[U,S]): S
  def flatMap[U, S[X]](f: T => S[U])(implicit canFlatMapSignal: CanFlatMapSignal[Signal, S]): S[U]
}



/**
 * A Signal in FRP represents a continuous value.
 * Here it is represented by the Signal trait, which is currently implemented in terms of a 'now' value
 * and a 'change' event stream. Transformations are implemented around those two members.
 * To obtain a signal, see Var, Val, Timer, and BufferSignal. In addition, new signals can be derived from
 * existing signals using the transformation methods defined in this trait.
 * @param T the type of value this signal contains
 */
//TODO transformations to not need to take an Observing--see parallel comment in EventStream
//TODO provide change veto (cancel) support
trait SimpleSignal[T] extends Signal[T] { parent =>
  
  
  /**
   * Represents the current value. Often, this value does not need to be
   * (or should not be) used explicitly from the outside; instead you can pass functions
   * that operate on the value, to the Signal.
   */
  def now: T
  
  /**
   * Returns an EventStream that, every time this signal's value changes, fires
   * an event consisting of the new value.
   */
  def change: EventStream[T]
  
//  /**
//   * Return a new Signal whose value is computed from the value
//   * of this Signal, transformed by f. It fires change events
//   * whenever (and only when) the original Signal does, but the
//   * event values are transformed by f.
//   * For example:
//   * val a: Signal[Int] = ...
//   * val b = a.map(_ + 1)
//   * b represents a Signal whose value is always 1 greater than a.
//   * Whenever a fires an event of x, b fires an event of x+1.
//   */
  def map[U, S](f: T=>U)(implicit canMapSignal: CanMapSignal[U, S]): S = canMapSignal.map(this, f)
  
  /**
   * Returns a new signal, that for every value of this parent signal,
   * will correspond to the signal resulting from applying f to
   * the respective value of this parent signal.
   * Whenever this Signal's change EventStream fires, the
   * resulting Signal's change EventStream will fire the
   * value of the new signal, and subsequently will fire
   * all change events fired by that signal.
   * This can be used to build a signal that switches
   * among several other signals.
   * For example:
   * val sa: Signal[Int] = ...
   * def sb(a: Int): Signal[Int] = a.map(_ + 1)
   * val sc = sa.flatMap(a => sb(a))
   * 
   * If the function is typed to return a SeqSignal, its deltas and changes correspond to
   * those of the SeqSignals returned by ''f'', after each invocation
   * of ''f''.
   * In addition, every change to the parent results in a change event
   * as well as deltas reflecting the transition from the SeqSignal
   * previously returned by ''f'' and the on returned by it now.
   */
  def flatMap[U, S[X]](f: T => S[U])(implicit canFlatMapSignal: CanFlatMapSignal[Signal, S]): S[U] = canFlatMapSignal.flatMap(this, f)
  
  //TODO differentiate types at runtime rather than compile time?
  //Maybe use some kind of manifest or other evidence?
  //Or have f implicitly wrapped in some wrapper?
  /*def flatMap[U](f: T => SeqSignal[U]) = new FlatMappedSeqSignal[T,U](this,f)*//* {
    //TODO cache
    def now = currentSignal.now
    private var currentSignal = f(parent.now)
    lazy val change0 = parent.change.flatMap(parent.now){_ => f(parent.now).change}
    change0 addListener change.fire
    override lazy val change = new EventStream[Seq[U]] {}
    private val startDeltas = now.zipWithIndex.map{case (e,i)=>Include(i,e)}
    
    private def fireDeltaDiff(lastDeltas: Seq[Message[T,U]], newSeq: Seq[U]): Seq[Message[T,U]] = {
      val (da, db) = (prev, Batch(n.transform.baseDeltas.map{_.asInstanceOf[Message[T,U]]}: _*).flatten)
      val toUndo = da.filterNot(db.contains) map {_.inverse} reverse
      val toApply = db.filterNot(da.contains)
      deltas fire Batch(toUndo ++ toApply map {_.asInstanceOf[Message[U,U]]}: _*)
      db
    }
    parent.change.foldLeft[Seq[Message[T,U]]](startDeltas){(prev: Seq[Message[T,U]], cur: T) =>
      //TODO should we do use a direct diff of the seqs instead? 
//      println("Entering foldLeft")
      val n = f(cur)
      change fire n.transform
//      println(n.transform.getClass)
//      println(n.transform)
      fireDeltaDiff(prev, n.transform)
    }
    lazy val deltas0 = parent.change.flatMap(parent.now){_ => f(parent.now).deltas}
    deltas0 addListener deltas.fire
    override lazy val deltas = new EventStream[Message[U,U]] {}
  }*/

  
  
//  /**
//   * 
//   * @param f
//   * @return a SeqSignal whose deltas and change events correspond to
//   * those of the SeqSignals returned by ''f'', after each invocation
//   * of ''f'', which result from change events fired by the parent signal.
//   * In addition, every change to the parent results in a change event
//   * as well as deltas reflecting the transition from the SeqSignal
//   * previously returned by ''f'' and the on returned by it now.
//   */
//  //TODO differentiate types at runtime rather than compile time?
//  //Maybe use some kind of manifest or other evidence?
//  //Or have f implicitly wrapped in some wrapper?
//  def flatMap[U](f: T => SeqSignal[U]) = new FlatMappedSeqSignal[T,U](this, f) 
  
  
//  def map[U, S2](f: T => U)(implicit mapper: SignalMapper[U, This, S2]): S2 = mapper(this.asInstanceOf[This[T]], f)

}


protected class MappedSignal[T,U](private val parent: Signal[T], f: T=>U) extends SimpleSignal[U] {
  import scala.ref.WeakReference
  private val emptyCache = new WeakReference[Option[U]](None)
  protected var cached = emptyCache
  protected val cache = {v: U =>
    cached = new WeakReference(Some(v))
  }
  def now = cached.get match {
    case None | Some(None) =>
      val ret = f(parent.now)
      cache(ret)
      ret
    case Some(Some(v)) =>
      v
  }
  
  /**
   * Fire change events whenever (the outer) Signal.this changes,
   * but the events should be transformed by f
   */
  lazy val change = parent.change.map(f)

  //TODO we need a way to be able to do this only if there are no real listeners
  change addListener cache
}

trait CanMapSignal[U, S] {
  def map[T](parent: Signal[T], f: T=>U): S
}

trait LowPriorityCanMapSignalImplicits {
  implicit def canMapSignal[U]: CanMapSignal[U, Signal[U]] = new CanMapSignal[U, Signal[U]] {
    def map[T](parent: Signal[T], f: T=>U): Signal[U] = new MappedSignal[T,U](parent,f)
  }
}
object CanMapSignal extends LowPriorityCanMapSignalImplicits {
  implicit def canMapSeqSignal[E]: CanMapSignal[TransformedSeq[E], SeqSignal[E]] = new CanMapSignal[TransformedSeq[E], SeqSignal[E]] {
    def map[T](parent: Signal[T], f: T=>TransformedSeq[E]): SeqSignal[E] = new MappedSeqSignal[T,E](parent,f)
  }
}

trait CanFlatMapSignal[S1[T], S2[T]] {
  def flatMap[T, U](parent: S1[T], f: T => S2[U]): S2[U]
}

trait LowPriorityCanFlatMapSignalImplicits {
  implicit def canFlatMapSignal: CanFlatMapSignal[Signal, Signal] = new CanFlatMapSignal[Signal, Signal] {
    def flatMap[T, U](parent: Signal[T], f: T=>Signal[U]): Signal[U] = new FlatMappedSignal[T,U](parent,f)
  }
}
object CanFlatMapSignal extends LowPriorityCanFlatMapSignalImplicits {
  implicit def canFlatMapSeqSignal: CanFlatMapSignal[Signal, SeqSignal] = new CanFlatMapSignal[Signal, SeqSignal] {
    def flatMap[T, U](parent: Signal[T], f: T=>SeqSignal[U]): SeqSignal[U] = new FlatMappedSeqSignal[T,U](parent,f)
  }
}


protected class FlatMappedSignal[T,U](private val parent: Signal[T], f: T=>Signal[U]) extends SimpleSignal[U] {
  def now = currentMappedSignal.now
  // We send out own change events as we are an aggregate signal
  val change = new EventSource[U] {}
  private val listener: U => Unit = { x => change fire x}
  // Currently mapped value
  private var currentMappedSignal = f(parent.now)
  // Register our first listener
  currentMappedSignal.change addListener listener
  // When the parent changes, we need to update our forwarding listeners and send the new state of this aggregate signal.
  private val parentListener = { x: T =>
    currentMappedSignal.change removeListener listener
    currentMappedSignal = f(x)
    currentMappedSignal.change addListener listener
    listener(now)
  }
  parent.change addListener parentListener
}
protected class FlatMappedSeqSignal[T,U](private val parent: Signal[T], f: T=>SeqSignal[U]) extends SeqSignal[U] {
  def now = currentMappedSignal.now
  override lazy val change = new EventSource[TransformedSeq[U]] {}
  private val changeListener: TransformedSeq[U]=>Unit = change.fire _
  private val deltasListener: Message[U,U]=>Unit = deltas.fire _
  private var currentMappedSignal = f(parent.now)
//  private var lastDeltas: Seq[Message[T,U]] = now.zipWithIndex.map{case (e,i)=>Include(i,e)}
  private var lastSeq: Seq[U] = now
  currentMappedSignal.change addListener changeListener
  currentMappedSignal.deltas addListener deltasListener
  
  private val parentChangeListener: T=>Unit = { x =>
    currentMappedSignal.change removeListener changeListener
    currentMappedSignal.deltas removeListener deltasListener
    currentMappedSignal = f(x)
    val n = currentMappedSignal.transform
    change.fire(n)
    lastSeq = fireDeltaDiff(lastSeq, n)
    currentMappedSignal.change addListener changeListener
    currentMappedSignal.deltas addListener deltasListener
  }
  parent.change addListener parentChangeListener
  
  private def fireDeltaDiff(lastSeq: Seq[U], newSeq: Seq[U]): Seq[U] = {
    deltas fire Batch(LCS.lcsdiff(lastSeq, newSeq, (_:U) == (_:U)): _*)
    newSeq
  }
  
//  BROKEN!! Makes false assumptions about baseDeltas 
//  Perhaps baseDeltas should be deprecated or changed 
//  private def fireDeltaDiff(lastDeltas: Seq[Message[T,U]], newSeq: TransformedSeq[U]): Seq[Message[T,U]] = {
//    val newDeltas: Seq[Message[T,U]] =
//      Batch[T,U](newSeq.baseDeltas.toList.collect{case m: Message[T,U] => m}: _*).flatten
//    
//    //FIXME!!
//    var shift = 0
//    val normalized = newDeltas map {
//      case Include(i, e) =>
//        Include(i, e)
//      case Remove(i, e) =>
//        shift += 1
//        Remove(i + shift - 1, e)
//    }
//
//    println("lastDeltas: " + lastDeltas)
//    println("normalized: " + normalized)
//    val toUndo = lastDeltas.map(_.inverse).filterNot(normalized.contains) reverse
//    val toApply = normalized.filterNot(lastDeltas.contains) //TODO lastDelta?? should be toUndo??
//    println("toUndo: " + toUndo)
//    println("toApply: " + toApply)
//    deltas fire Batch(toUndo ++ toApply map {_.asInstanceOf[Message[U,U]]}: _*)
//    normalized
//  }

  override def toString = "FlatMappedSeqSignal("+parent+","+System.identityHashCode(f)+")"
}




/**
 * A signal representing a value that never changes
 * (and hence never fires change events)
 */
case class Val[T](now: T) extends SimpleSignal[T] {
  def change = new EventSource[T] {}
}

/**
 * Defines a factory for Vars
 */
object Var {
  def apply[T](v: T) = new Var(v)
}
/**
 * A signal whose value can be changed directly
 */
class Var[T](initial: T) extends SimpleSignal[T] {
  private var _value = initial
  
  def now = value
  //TODO do we need value? why not just now and now_= ? Or just now and update?
  //Advantage of setter other than update is to allow for += type assignments
  // 'var.value += 2' works; 'var ()+= 2' does not work.
  def value = _value
  /**
   * Setter. Usage: var.value = x
   */
  def value_=(v: T) {
    _value = v
    change0.fire(v)
  }
  /**
   * Usage: var()=x
   */
  final def update(v: T) = value = v
  
  /**
   * Fires an event after every mutation, consisting of the new value
   */
  lazy val change: EventStream[T] = change0
  private lazy val change0 = new EventSource[T] {}

  override def toString = "Var("+now+")"  
}

private object _timer extends java.util.Timer {
  def scheduleAtFixedRate(delay: Long, interval: Long)(p: =>Unit) =
    super.scheduleAtFixedRate(new java.util.TimerTask {def run = p}, delay, interval)
}

/**
 * A signal whose value represents elapsed time in milliseconds, and is updated
 * on a java.util.Timer thread.
 * @param startTime the value this signal counts up from
 * @param interval the frequency at which to update the signal's value.
 */
//TODO should this really extend Var?
//TODO could/should this be implemented as a RefreshingVar?
class Timer(private val startTime: Long = 0, interval: Long) extends Var(startTime) {
  private val origMillis = System.currentTimeMillis
  _timer.scheduleAtFixedRate(interval, interval){
    value = System.currentTimeMillis - origMillis + startTime
  }
}

/**
 * A Var that updates itself based on the supplied call-by-name
 * regularly, at a given interval, on a java.util.Timer thread.
 * @param interval the rate at which to update self
 * @param supplier a call-by-name that calculates the signal's value
 */
//TODO should this really extend Var?
class RefreshingVar[T](interval: Long)(supplier: =>T) extends Var(supplier) {
  _timer.scheduleAtFixedRate(interval, interval){value = supplier}
}
/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional (used to stash incoming operations during garbage collection)
  var pendingQueue = Queue.empty[Operation]

  def receive = normal

  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case Insert(requester, id, elem) => 
      root ! Insert(requester, id, elem)
    case Contains(requester, id, elem) => 
      root ! Contains(requester, id, elem)
    case Remove(requester, id, elem) => 
      root ! Remove(requester, id, elem)
    case GC =>
      var newRoot = createRoot
      root ! CopyTo(newRoot)
      context.become(garbageCollecting(newRoot))
  }

  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
    case Insert(requester, id, elem) => 
      pendingQueue = pendingQueue.enqueue(Insert(requester, id, elem))
    case Contains(requester, id, elem) => 
      pendingQueue = pendingQueue.enqueue(Contains(requester, id, elem))
    case Remove(requester, id, elem) => 
      pendingQueue = pendingQueue.enqueue(Remove(requester, id, elem)) 
    case CopyFinished =>
      pendingQueue.foreach(newRoot ! _)
      pendingQueue = Queue.empty
      root ! PoisonPill
      root = newRoot
      context.become(normal)
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  /**
   * Acknowledges that a copy has been completed. This message should be sent
   * from a node to its parent, when this node and all its children nodes have
   * finished being copied.
   */
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  def receive = normal

  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = { 
    case Insert(requester, id, elem) => 
      if(this.elem == elem)
        removed = false
        requester ! OperationFinished(id)
      else 
        val x = context.actorOf(BinaryTreeNode.props(elem, initiallyRemoved = false))
        if(this.elem < elem)
          if(subtrees.contains(Right))
            subtrees(Right) ! Insert(requester, id, elem)
          else
            subtrees += (Right -> x)
            requester ! OperationFinished(id)
        else 
          if(subtrees.contains(Left))
            subtrees(Left) ! Insert(requester, id, elem)
          else
            subtrees += (Left -> x)
            requester ! OperationFinished(id)
    case Contains(requester, id, elem) => 
      if(this.elem == elem)
        requester ! ContainsResult(id, !removed)
      else
        if(this.elem < elem)
          if(subtrees.contains(Right))
            subtrees(Right) ! Contains(requester, id, elem)
          else
            requester ! ContainsResult(id, false)
        else 
          if(subtrees.contains(Left))
            subtrees(Left) ! Contains(requester, id, elem)
          else
            requester ! ContainsResult(id, false)
    case Remove(requester, id, elem) => 
      if(this.elem == elem)
          removed = true
          requester ! OperationFinished(id)
      else
        if(this.elem < elem)
          if(subtrees.contains(Right))
            subtrees(Right) ! Remove(requester, id, elem)
          else
            requester ! OperationFinished(id)
        else 
          if(subtrees.contains(Left))
            subtrees(Left) ! Remove(requester, id, elem)
          else
            requester ! OperationFinished(id)
    case CopyTo(newRoot) =>
      if(!removed)
        newRoot ! Insert(self, this.elem, this.elem)
      if(subtrees.isEmpty && removed)
        context.parent ! CopyFinished
      else
        subtrees.values.foreach(_ ! CopyTo(newRoot))
      context.become(copying(subtrees.values.toSet, removed))
    }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = {
      case OperationFinished(id) =>
        if(expected.isEmpty)
          context.parent ! CopyFinished
        else
          context.become(copying(expected, true))
      case CopyFinished =>
        val x = expected - sender
        if(x.isEmpty && insertConfirmed)
          context.parent ! CopyFinished
        else 
          context.become(copying(x, insertConfirmed))
  }
}

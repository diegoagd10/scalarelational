package com.outr.query.h2

import com.outr.query.table.property.TableProperty
import com.outr.query.h2.trigger.TriggerType

/**
 * Triggers property can be added to a table to specify that it will support triggers.
 *
 * @author Matt Hicks <matt@outr.com>
 */
class Triggers(triggerTypes: Set[TriggerType]) extends TableProperty {
  override def name = Triggers.name

  def has(triggerType: TriggerType) = triggerTypes.contains(triggerType)
}

/**
 * Only triggers on Insert, Update, and Delete. This explicitly excludes Select. To configure different triggers create
 * a new instance.
 */
object Triggers {
  val name = "triggers"

  val All = new Triggers(Set(TriggerType.Insert, TriggerType.Update, TriggerType.Delete, TriggerType.Select))
  val Normal = new Triggers(Set(TriggerType.Insert, TriggerType.Update, TriggerType.Delete))
}
package engineering.sketches.strukt.type

import engineering.sketches.kontour.Item
import engineering.sketches.kontour.Type

class Container(val type: Type? = null, val systems: Set<Item>? = null) : Type() {
    enum class Type {
        Service, Storage, Queue
    }
}

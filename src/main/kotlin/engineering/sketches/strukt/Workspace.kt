/*
* Copyright 2025 Nikita Demin
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* */

package engineering.sketches.strukt

import com.structurizr.Workspace
import com.structurizr.model.*
import com.structurizr.util.WorkspaceUtils
import com.structurizr.view.AutomaticLayout
import com.structurizr.view.Shape
import engineering.sketches.kontour.Item
import engineering.sketches.kontour.Tag
import engineering.sketches.kontour.Vertex
import engineering.sketches.strukt.type.System
import engineering.sketches.strukt.type.Container
import engineering.sketches.strukt.type.Component
import engineering.sketches.strukt.type.Person
import java.io.File

private const val SYNTHETIC_TAG = "Synthetic"

class Workspace(private val name: String, private val description: String) {
    fun draw(vertices: Set<Vertex>) {
        val elements = mutableMapOf<Vertex, Element>()
        val landscapes = mutableMapOf<Tag, MutableList<SoftwareSystem>>()
        val syntheticSystems = mutableMapOf<Item, MutableList<Vertex>>()

        val workspace = Workspace(name, description)

        val model = workspace.model
        val views = workspace.views

        model.impliedRelationshipsStrategy = CreateImpliedRelationshipsUnlessSameRelationshipExistsStrategy()

        for (vertex in vertices) {
            when {
                vertex.hasType(System::class) -> {
                    val softwareSystem = model.addSoftwareSystem(vertex.name, vertex.summary)
                    for (inner in vertex.composed + vertex.aggregated) {
                        if (inner.hasType(Container::class)) {
                            val container = softwareSystem.addContainer(inner.name, inner.summary)
                            inner.asType(Container::class)?.type?.let { container.addTags(it.name) }
                            for (composite in inner.composed) {
                                if (composite.hasType(Component::class)) {
                                    elements[composite] = container.addComponent(composite.name, composite.summary)
                                }
                            }
                            elements[inner] = container
                        }
                    }
                    elements[vertex] = softwareSystem
                    for (landscape in vertex.asType(System::class)?.landscapes ?: emptySet()) {
                        val list = landscapes.computeIfAbsent(landscape) { _ -> mutableListOf() }
                        list.add(softwareSystem)
                    }
                }
                vertex.hasType(Person::class) -> {
                    elements[vertex] = model.addPerson(vertex.name, vertex.summary)
                }
                vertex.hasType(Container::class) -> {
                    vertex.asType(Container::class)?.systems?.filter { it.hasType(System::class) }?.forEach {
                        val list = syntheticSystems.computeIfAbsent(it) { _ -> mutableListOf() }
                        list.add(vertex)
                    }
                }
            }
        }

        for ((item, containerVertices) in syntheticSystems) {
            val softwareSystem = model.addSoftwareSystem(item.name, item.description)
            softwareSystem.addTags(SYNTHETIC_TAG)
            for (vertex in containerVertices) {
                val container = softwareSystem.addContainer(vertex.name, vertex.summary)
                vertex.asType(Container::class)?.type?.let { container.addTags(it.name) }
                for (composite in vertex.composed) {
                    if (composite.hasType(Component::class)) {
                        elements[composite] = container.addComponent(composite.name, composite.summary)
                    }
                }
                elements[vertex] = container
            }
            for (landscape in item.asType(System::class)?.landscapes ?: emptySet()) {
                val list = landscapes.computeIfAbsent(landscape) { _ -> mutableListOf() }
                list.add(softwareSystem)
            }
        }

        for ((vertex, element) in elements) {
            vertex.addRelationship(element, elements)
        }

        val direction = AutomaticLayout.RankDirection.LeftRight
        for (system in elements.values.filterIsInstance<SoftwareSystem>()) {
            views.createSystemContextView(system, system.name, system.description).run {
                addDefaultElements()
                removeRelationshipsNotConnectedToElement(system)
                removeElementsWithNoRelationships()
                enableAutomaticLayout(direction)
            }
            if (system.hasContainers()) {
                views.createContainerView(system, "${system.name}-Containers", system.description).run {
                    addDefaultElements()
                    val synthetic = getElements()
                        .map { it.element }
                        .filter { it.hasTag(SYNTHETIC_TAG) }
                        .map { it as SoftwareSystem }
                    synthetic.forEach {
                        remove(it)
                        it.containers.forEach(::add)
                    }
                    removeElementsWithNoRelationships()
                    enableAutomaticLayout(direction)
                }
            }
            for (container in system.containers) {
                if (container.hasComponents()) {
                    views.createComponentView(container, container.name, container.description).run {
                        addDefaultElements()
                        val synthetic = getElements()
                            .map { it.element }
                            .filter { it.hasTag(SYNTHETIC_TAG) }
                            .map { it as SoftwareSystem }
                        synthetic.forEach {
                            remove(it)
                            it.containers.forEach(::add)
                        }
                        removeElementsWithNoRelationships()
                        enableAutomaticLayout(direction)
                    }
                }
            }
        }

        for ((landscape, systems) in landscapes) {
            views.createSystemLandscapeView(landscape.name, landscape.description).run {
                systems.forEach(::add)
                addAllPeople()
                removeElementsWithNoRelationships()
                enableAutomaticLayout(direction)
            }
        }

        with(views.configuration.styles) {
            addElementStyle(Tags.PERSON).shape(Shape.Person)
            addElementStyle(Tags.COMPONENT).shape(Shape.RoundedBox)
            addElementStyle(Container.Type.Service.name).shape(Shape.Hexagon)
            addElementStyle(Container.Type.Storage.name).shape(Shape.Cylinder)
            addElementStyle(Container.Type.Queue.name).shape(Shape.Pipe).width(1200).height(200)
        }

        WorkspaceUtils.saveWorkspaceToJson(workspace, File("doc/architecture.json"))
    }
}

private fun Vertex.addRelationship(model: Element, elements: Map<Vertex, Element>) {
    for ((interaction, destination) in edges) {
        when(model) {
            is CustomElement -> elements[destination]?.let {
                when (it) {
                    is CustomElement ->
                        model.uses(it, interaction.summary, "", InteractionStyle.Synchronous)
                    is StaticStructureElement ->
                        model.uses(it, interaction.summary, "", InteractionStyle.Synchronous)
                    else -> {}
                }
            }
            is StaticStructureElement -> elements[destination]?.let {
                when (it) {
                    is CustomElement ->
                        model.uses(it, interaction.summary)
                    is StaticStructureElement ->
                        model.uses(it, interaction.summary, "", InteractionStyle.Synchronous)
                    else -> {}
                }
            }
        }
    }
}

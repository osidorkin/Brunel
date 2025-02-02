/*
 * Copyright (c) 2015 IBM Corporation and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.brunel.build.d3;

import org.brunel.action.Param;
import org.brunel.build.util.PositionFields;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Field;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

/**
 * sc
 * Handles adding interactivity to charts
 */
class D3Interaction {

    private static boolean isSelectable(VisSingle vis) {
        // Only if explicitly requested
        return vis.tInteraction.containsKey(VisTypes.Interaction.select);
    }

    private final D3ScaleBuilder scales;       // Scales for the chart
    private final ScriptWriter out;             // Write definitions here
    private final boolean zoomable;             // Same for all elements
    private final boolean xScaleCategorical;    // True if any are
    private final boolean yScaleCategorical;    // True if any are

    public D3Interaction(VisSingle[] elements, PositionFields positionFields, D3ScaleBuilder scales, ScriptWriter out) {
        this.scales = scales;
        this.out = out;
        this.xScaleCategorical = anyCategorical(positionFields.allXFields);
        this.yScaleCategorical = anyCategorical(positionFields.allYFields);
        this.zoomable = isZoomable(elements);
    }

    /**
     * This attaches event handlers to the element for click-selection
     */
    public void addElementHandlers(VisSingle vis) {
        if (isSelectable(vis)) {
            Param p = vis.tInteraction.get(VisTypes.Interaction.select);
            String type = "click";
            if (p.hasModifiers()) type = p.firstModifier().asString();
            out.add("selection.on('" + type + "', function(d) { BrunelD3.select(data.$row(d), original, this, rebuildSystem) } )").endStatement();

        }
    }

    /**
     * Set up the overlay group and shapes for trapping events for zooming.
     */
    public void addPrerequisites() {
        if (zoomable) {
            // The group for the overlay
            out.add("var overlay = interior.append('g').attr('class', 'element')")
                    .addChained("attr('class', 'overlay').style('cursor','move').style('fill','none').style('pointer-events','all')")
                    .endStatement();
            // Add an overlay rectangle for zooming that will trap all mouse events and use them for pan/zoom
            out.add("var zoom = d3.behavior.zoom().on('zoom', function() {build(-1)} )").endStatement();
            out.add("overlay.append('rect').attr('class', 'overlay')")
                    .addChained("attr('width', geom.inner_width)")
                    .addChained("attr('height', geom.inner_height)")
                    .addChained("call(zoom)").endStatement();
        }
    }

    /**
     * Zooming modifies the scales, and this code makes that happen
     */
    public void addScaleInteractivity() {
        if (!zoomable) return;
        out.add("zoom");
        if (scales.coords == VisTypes.Coordinates.transposed) {
            // Attach x to y and y to x
            if (!xScaleCategorical) out.add(".y(scale_x)");
            if (!yScaleCategorical) out.add(".x(scale_y)");
        } else {
            if (!xScaleCategorical) out.add(".x(scale_x)");
            if (!yScaleCategorical) out.add(".y(scale_y)");
        }
        out.endStatement();
    }

    private boolean anyCategorical(Field[] fields) {
        for (Field f : fields) if (f.preferCategorical()) return true;
        return false;
    }

    private boolean isZoomable(VisSingle[] elements) {
        // Check for things that just will not work currently
        if (xScaleCategorical && yScaleCategorical) return false;                           // Only zoom numerical
        if (scales.isDiagram || scales.coords == VisTypes.Coordinates.polar) return false;  // Doesn't work

        // If anything says we want it, we get it
        // Otherwise, if anything says we do not, we do not
        // Otherwise, we get it
        boolean defaultResult = true;
        for (VisSingle vis : elements) {
            if (vis.tInteraction.containsKey(VisTypes.Interaction.panzoom)) return true;
            if (vis.tInteraction.containsKey(VisTypes.Interaction.none)) defaultResult = false;
        }
        return defaultResult;
    }
}

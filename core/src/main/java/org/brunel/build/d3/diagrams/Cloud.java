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

package org.brunel.build.d3.diagrams;

import org.brunel.build.d3.ElementDefinition;
import org.brunel.build.util.ElementDetails;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Dataset;
import org.brunel.model.VisSingle;

class Cloud extends D3Diagram {

    public Cloud(VisSingle vis, Dataset data, ScriptWriter out) {
        super(vis, data, out);
    }

    public ElementDetails writeDataConstruction() {
        out.comment("Build the cloud layout");
        out.add("var cloud = BrunelD3.cloudLayout(processed, [geom.inner_width, geom.inner_height])").endStatement();
        out.add("function keyFunction(d) { return d.key }").endStatement();
        // The labeling will be defined later and then used when we do the actual layout call to define the D3 data
        return ElementDetails.makeForDiagram("data._rows", "text",  "text", "box", true);

    }

    public void writeDefinition(ElementDetails details, ElementDefinition elementDef) {
        // Set the given location using the transform
        out.addChained("attr('transform', cloud.transform)").endStatement();
        addAestheticsAndTooltips(details, false);
    }

    public void writeDiagramEnter() {
        // The cloud needs to set all this stuff up front
        out.addChained("attr('dy', '0.3em').style('text-anchor', 'middle').classed('label', true)")
                .addChained("text(labeling.content)");
        labelBuilder.addFontSizeAttribute(vis);
        out.endStatement();
    }
}

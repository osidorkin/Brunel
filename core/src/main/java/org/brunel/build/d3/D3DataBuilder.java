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
import org.brunel.build.DataTransformParameters;
import org.brunel.build.util.BuilderOptions;
import org.brunel.build.util.ScriptWriter;
import org.brunel.data.Data;
import org.brunel.data.Dataset;
import org.brunel.data.Field;
import org.brunel.data.summary.FieldRowComparison;
import org.brunel.data.util.DateFormat;
import org.brunel.data.util.Range;
import org.brunel.model.VisItem;
import org.brunel.model.VisSingle;
import org.brunel.model.VisTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Write the Javascript for the data
 */
public class D3DataBuilder {
    private final VisSingle vis;
    private final ScriptWriter out;
    private final Dataset data;
    private final int datasetIndex;

    public D3DataBuilder(VisSingle vis, ScriptWriter out, Dataset data, int index) {
        this.vis = vis;
        this.out = out;
        this.data = data;
        datasetIndex = index;
    }

    public void writeDataManipulation(Map<String, Integer> requiredFields) {
        out.onNewLine().ln().add("function makeData() {").ln().indentMore();
        writeDataTransforms();
        writeHookup(requiredFields);
        out.indentLess().onNewLine().add("}").endStatement().ln();
    }

    public static void writeTables(VisItem main, ScriptWriter out, BuilderOptions options) {
        if (options.includeData == BuilderOptions.DataMethod.none) return;
        if (options.includeData == BuilderOptions.DataMethod.minimal) {
            throw new UnsupportedOperationException("Cannot make minimal data yet");
        }

        Dataset[] datasets = main.getDataSets();
        for (int d = 0; d < datasets.length; d++) {
            Dataset data = datasets[d];
            Field[] fields;

            if (options.includeData == BuilderOptions.DataMethod.columns) {
                // Only the fields needed by the vis items
                LinkedHashSet<Field> fieldsAsSet = new LinkedHashSet<Field>();
                addUsedFields(main, data, fieldsAsSet);
                fields = fieldsAsSet.toArray(new Field[fieldsAsSet.size()]);
            } else {
                // All the fields
                fields = data.fields;
            }

            if (fields.length == 0) {
                // A Chart that doesn't actually use the data ... just meta values
                fields = new Field[] { Data.makeConstantField("_dummy_", "Dummy", 1.0, data.rowCount())};
            }

            // Name the table with a numeric suffix for multiple tables
            out.onNewLine().add("var", String.format(options.dataName, d + 1), "= [").ln().indentMore();
            int numPerLine = 12 / Math.max(1, Math.max(1, fields.length));
            out.add("[");
            for (int i = 0; i < fields.length; i++) {
                String name = fields[i].name;
                if (name.startsWith("#")) continue;
                if (i > 0) out.add(", ");
                out.add("'").add(name).add("'");
            }
            out.add("],");
            for (int r = 0; r < data.rowCount(); r++) {
                if (r > 0) out.add(",");
                if ((r + 1) % numPerLine == 0) out.onNewLine();
                out.add(makeRowText(fields, r));
            }
            out.indentLess().onNewLine().add("]").endStatement();
        }
    }

    private static void addUsedFields(VisItem item, Dataset data, Collection<Field> fields) {
        if (item.children() == null) {
            VisSingle vis = (VisSingle) item;                           // No children => VisSingle
            if (vis.getDataset() != data) return;                       // Does not use this data set, so ignore it
            for (String f : vis.usedFields(true))                       // Yes! Add in the fields to be used
                if (!f.startsWith("#")) {                               // .. but not synthetic fields
                    Field field = data.field(f, true);                  // Constant fields will not be found
                    if (field != null) fields.add(field);
                }
        } else {
            for (VisItem i : item.children())                           // Pass down to child items
                addUsedFields(i, data, fields);
        }
    }

    private void defineKeyFieldFunction(List<String> fields, boolean actsOnRowObject, Map<String, Integer> usedFields) {
        // Add the split fields accessor
        out.add("function(d) { return ");
        if (fields.isEmpty()) {
            out.add("'ALL'");
        } else {
            for (int i = 0; i < fields.size(); i++) {
                String s = fields.get(i);
                if (i > 0) out.add("+ '|' + ");
                out.add("f" + usedFields.get(s));
                out.add(actsOnRowObject ? ".value(d.row)" : ".value(d)");
            }
        }
        out.add(" }");
    }

    /**
     * The keys are used so that transitions when the data changes are logically consistent.
     * We want the right things to morph into each color as the data changes, and not be
     * dependent on table order. The following code works out the most likely items to be the keys
     * based on the type of chart being produced
     *
     * @return list of keys
     */
    private List<String> makeKeyFields() {
        // If we have defined keys, util them
        if (!vis.fKeys.isEmpty()) return asFields(vis.fKeys);

        if (vis.tDiagram == VisTypes.Diagram.chord) {
            List<String> result = new ArrayList<String>();
            Collections.addAll(result, vis.positionFields());
            Collections.addAll(result, vis.aestheticFields());
            if (suitableForKey(result)) return result;
        }

        if (vis.tDiagram == VisTypes.Diagram.tree || vis.tDiagram == VisTypes.Diagram.treemap || vis.tDiagram == VisTypes.Diagram.map) {
            // Positions are the keys for trees and treemaps
            return Arrays.asList(vis.positionFields());
        }


        // If we split by aesthetics, they are the keys
        if (vis.tElement.producesSingleShape) return makeSplitFields();

        // For non-diagrams, try the aesthetics AND x values
        if (vis.tDiagram == null) {
            List<String> result = asFields(vis.fX);
            Collections.addAll(result, vis.aestheticFields());
            if (suitableForKey(result)) return result;
        }


        // Otherwise just use the row
        return Collections.singletonList("#row");
    }

    private List<String> asFields(List<Param> items) {
        List<String> fields = new ArrayList<String>();
        for (Param p : items) fields.add(p.asField());
        return fields;
    }

    // If the row contains any nulls, return null for the whole row
    private static String makeRowText(Field[] fields, int r) {
        StringBuilder row = new StringBuilder();
        D3Util.DateBuilder dateBuilder = new D3Util.DateBuilder();
        row.append("[");
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (field.name.startsWith("#")) continue;           // Skip special fields
            if (i > 0) row.append(", ");
            Object value = field.value(r);
            if (value == null) {
                row.append("null");
            } else if (value instanceof Range) {
                row.append(Data.quote(value.toString()));
            } else if (field.isDate()) {
                Date date = Data.asDate(value);
                if (date == null) return null;
                row.append(dateBuilder.make(date, (DateFormat) field.property("dateFormat"), false));
            } else if (field.isNumeric()) {
                Double d = Data.asNumeric(value);
                if (d == null) return null;
                row.append(d.toString());
            } else
                row.append(Data.quote(value.toString()));
        }
        row.append("]");
        return row.toString();
    }

    private boolean suitableForKey(List<String> result) {
        // TODO: Cannot check if the fields make a good key easily without data
        Field[] fields = new Field[result.size()];
        for (int i = 0; i < fields.length; i++) fields[i] = data.field(result.get(i));
        // Sort and see if any adjacent 'keys' are the same
        FieldRowComparison rowComparison = new FieldRowComparison(fields, null, false);
        int[] order = rowComparison.makeSortedOrder(data.rowCount());
        for (int i = 1; i < order.length; i++)
            if (rowComparison.compare(order[i], order[i - 1]) == 0) return false;
        return true;
    }

    private void writeDataTransforms() {
        // The parameters are stored in the data set when it is transformed
        DataTransformParameters params = (DataTransformParameters) data.property("parameters");
        D3Util.addTiming("Data Start", out);
        out.add("original = datasets[" + datasetIndex + "]").endStatement();
        out.add("processed = pre(original,", datasetIndex, ")");
        out.mark();
        writeTransform("addConstants", params.constantsCommand);

        // Check for selection filtering
        Param param = vis.tInteraction.get(VisTypes.Interaction.filter);
        if (param != null) {
            if ("unselected".equals(param.asString()))
                writeTransform("filter", "#selection is " + Field.VAL_UNSELECTED);
            else
                writeTransform("filter", "#selection is " + Field.VAL_SELECTED);
        }

        writeTransform("filter", params.filterCommand);
        writeTransform("bin", params.transformCommand);
        writeTransform("summarize", params.summaryCommand);

        // Because series creates duplicates of fields, it is an expensive transformation
        // So we do not want to make it work on all fields, only the fields that are necessary.
        // So we reduce the data set to only necessary fields (summarize already does this, so
        // this step is not needed if summarize has been performed)
        if (!params.seriesCommand.isEmpty()) {
            if (params.summaryCommand.isEmpty()) writeTransform("reduce", params.usedCommand);
            writeTransform("series", params.seriesCommand);
        }
        writeTransform("sort", params.sortCommand);

        writeTransform("stack", params.stackCommand);               // Stack must come after all else

        out.endStatement();
        out.add("processed = post(processed,", datasetIndex, ")").endStatement();
        D3Util.addTiming("Data End", out);
    }

    private void writeHookup(Map<String, Integer> fieldsToIndex) {

        // Get the list of fields we need as an array
        String[] fields = new String[fieldsToIndex.size()];
        for (Map.Entry<String, Integer> e : fieldsToIndex.entrySet()) {
            fields[e.getValue()] = e.getKey();
        }

        out.onNewLine().add("var ");

        // Create references to the base fields
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) out.onNewLine();
            else out.indentMore();
            out.add("f" + i, "= processed.field(" + out.quote(fields[i]) + ")");
            if (i == fields.length - 1) out.endStatement();
            else out.add(",");
        }
        out.indentLess();

        // Define the key function
        out.add("var keyFunction = ");
        defineKeyFieldFunction(makeKeyFields(), false, fieldsToIndex);
        out.endStatement();

        out.add("data = {").ln().indentMore();

        // Add field definitions
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            String fieldID = D3Util.canonicalFieldName(fields[fieldIndex]);
            out.add(fieldID, ":").at(24).add("function(d) { return f" + fieldIndex + ".value(d.row) },").ln();
        }

        // Add formatted field definitions
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            String fieldID = D3Util.canonicalFieldName(fields[fieldIndex]);
            out.add(fieldID + "_f", ":").at(24).add("function(d) { return f" + fieldIndex + ".valueFormatted(d.row) },").ln();
        }
        // Add special items
        out.add("_split:").at(24);
        defineKeyFieldFunction(makeSplitFields(), true, fieldsToIndex);
        out.add(",").ln();
        out.add("_rows:").at(24).add("BrunelD3.makeRowsWithKeys(keyFunction, processed.rowCount())");

        if (vis.fKeys.size() == 1 && vis.fX.size() == 1 && vis.fY.size() == 1) {
            out.add(",").ln();
            String id = "f" + fieldsToIndex.get(vis.fKeys.get(0).asField());
            String x = "f" + fieldsToIndex.get(vis.fX.get(0).asField());
            String y = "f" + fieldsToIndex.get(vis.fY.get(0).asField());
            out.add("_idToPoint:").at(24).add("BrunelD3.locate(" + id, ", ", x, ",", y, ", processed.rowCount())");
        }

        out.onNewLine().indentLess().add("}").endStatement();
    }

    private List<String> makeSplitFields() {
        // Start with all the aesthetics
        ArrayList<String> splitters = new ArrayList<String>();

        // Always add splits and color
        for (Param p : vis.fSplits) splitters.add(p.asField());
        for (Param p : vis.fColor) splitters.add(p.asField());
        for (Param p : vis.fOpacity) splitters.add(p.asField());

        // We handle sized areas specially -- don't split using the size for them
        if (vis.tElement != VisTypes.Element.line && vis.tElement != VisTypes.Element.path) {
            for (Param p : vis.fSize) splitters.add(p.asField());
        }

        return splitters;
    }

    private void writeTransform(String name, String command) {
        // Ignore if nothing to write
        if (!command.isEmpty())
            out.addChained(name, "(" + out.quote(command) + ")");
    }

}

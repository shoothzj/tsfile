/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tsfile.write.chunk;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.common.conf.TSFileDescriptor;
import org.apache.tsfile.encoding.encoder.Encoder;
import org.apache.tsfile.encoding.encoder.TSEncodingBuilder;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.exception.write.PageException;
import org.apache.tsfile.file.header.PageHeader;
import org.apache.tsfile.file.metadata.enums.CompressionType;
import org.apache.tsfile.file.metadata.enums.TSEncoding;
import org.apache.tsfile.read.common.block.column.TimeColumn;
import org.apache.tsfile.utils.Binary;
import org.apache.tsfile.utils.TsPrimitiveType;
import org.apache.tsfile.write.schema.IMeasurementSchema;
import org.apache.tsfile.write.schema.VectorMeasurementSchema;
import org.apache.tsfile.write.writer.TsFileIOWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AlignedChunkWriterImpl implements IChunkWriter {

  protected TimeChunkWriter timeChunkWriter;
  protected List<ValueChunkWriter> valueChunkWriterList;
  protected int valueIndex;

  // Used for batch writing
  protected long remainingPointsNumber;

  protected AlignedChunkWriterImpl() {}

  // TestOnly
  public AlignedChunkWriterImpl(VectorMeasurementSchema schema) {
    timeChunkWriter =
        new TimeChunkWriter(
            schema.getMeasurementId(),
            schema.getCompressor(),
            schema.getTimeTSEncoding(),
            schema.getTimeEncoder());

    List<String> valueMeasurementIdList = schema.getSubMeasurementsList();
    List<TSDataType> valueTSDataTypeList = schema.getSubMeasurementsTSDataTypeList();
    List<TSEncoding> valueTSEncodingList = schema.getSubMeasurementsTSEncodingList();
    List<Encoder> valueEncoderList = schema.getSubMeasurementsEncoderList();

    valueChunkWriterList = new ArrayList<>(valueMeasurementIdList.size());
    for (int i = 0; i < valueMeasurementIdList.size(); i++) {
      valueChunkWriterList.add(
          new ValueChunkWriter(
              valueMeasurementIdList.get(i),
              schema.getCompressor(),
              valueTSDataTypeList.get(i),
              valueTSEncodingList.get(i),
              valueEncoderList.get(i)));
    }

    this.valueIndex = 0;
    this.remainingPointsNumber = timeChunkWriter.getRemainingPointNumberForCurrentPage();
  }

  /**
   * This is used to rewrite file. The encoding and compression of the time column should be the
   * same as the source file.
   *
   * @param timeSchema time schema
   * @param valueSchemaList value schema list
   */
  public AlignedChunkWriterImpl(
      IMeasurementSchema timeSchema, List<IMeasurementSchema> valueSchemaList) {
    timeChunkWriter =
        new TimeChunkWriter(
            timeSchema.getMeasurementId(),
            timeSchema.getCompressor(),
            timeSchema.getEncodingType(),
            timeSchema.getTimeEncoder());

    valueChunkWriterList = new ArrayList<>(valueSchemaList.size());
    for (int i = 0; i < valueSchemaList.size(); i++) {
      valueChunkWriterList.add(
          new ValueChunkWriter(
              valueSchemaList.get(i).getMeasurementId(),
              valueSchemaList.get(i).getCompressor(),
              valueSchemaList.get(i).getType(),
              valueSchemaList.get(i).getEncodingType(),
              valueSchemaList.get(i).getValueEncoder()));
    }

    this.valueIndex = 0;
    this.remainingPointsNumber = timeChunkWriter.getRemainingPointNumberForCurrentPage();
  }

  /**
   * This is used to write 0-level file. The compression of the time column is 'LZ4' in the
   * configuration by default. The encoding of the time column is 'TS_2DIFF' in the configuration by
   * default.
   *
   * @param schemaList value schema list
   */
  public AlignedChunkWriterImpl(List<IMeasurementSchema> schemaList) {
    TSEncoding timeEncoding =
        TSEncoding.valueOf(TSFileDescriptor.getInstance().getConfig().getTimeEncoder());
    TSDataType timeType = TSFileDescriptor.getInstance().getConfig().getTimeSeriesDataType();
    CompressionType timeCompression = TSFileDescriptor.getInstance().getConfig().getCompressor();
    timeChunkWriter =
        new TimeChunkWriter(
            "",
            timeCompression,
            timeEncoding,
            TSEncodingBuilder.getEncodingBuilder(timeEncoding).getEncoder(timeType));

    valueChunkWriterList = new ArrayList<>(schemaList.size());
    for (int i = 0; i < schemaList.size(); i++) {
      valueChunkWriterList.add(
          new ValueChunkWriter(
              schemaList.get(i).getMeasurementId(),
              schemaList.get(i).getCompressor(),
              schemaList.get(i).getType(),
              schemaList.get(i).getEncodingType(),
              schemaList.get(i).getValueEncoder()));
    }

    this.valueIndex = 0;

    this.remainingPointsNumber = timeChunkWriter.getRemainingPointNumberForCurrentPage();
  }

  public void write(long time, int value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, long value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, boolean value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, float value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, double value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, Binary value, boolean isNull) {
    valueChunkWriterList.get(valueIndex++).write(time, value, isNull);
  }

  public void write(long time, int value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, long value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, boolean value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, float value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, double value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, Binary value, boolean isNull, int valueIndex) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void write(long time, TsPrimitiveType[] points) {
    valueIndex = 0;
    for (TsPrimitiveType point : points) {
      ValueChunkWriter writer = valueChunkWriterList.get(valueIndex++);
      switch (writer.getDataType()) {
        case INT64:
        case TIMESTAMP:
          writer.write(time, point != null ? point.getLong() : Long.MAX_VALUE, point == null);
          break;
        case INT32:
        case DATE:
          writer.write(time, point != null ? point.getInt() : Integer.MAX_VALUE, point == null);
          break;
        case FLOAT:
          writer.write(time, point != null ? point.getFloat() : Float.MAX_VALUE, point == null);
          break;
        case DOUBLE:
          writer.write(time, point != null ? point.getDouble() : Double.MAX_VALUE, point == null);
          break;
        case BOOLEAN:
          writer.write(time, point != null ? point.getBoolean() : false, point == null);
          break;
        case TEXT:
        case BLOB:
        case STRING:
          writer.write(
              time,
              point != null ? point.getBinary() : new Binary("".getBytes(StandardCharsets.UTF_8)),
              point == null);
          break;
      }
    }
    write(time);
  }

  public void write(long time) {
    valueIndex = 0;
    timeChunkWriter.write(time);
    if (checkPageSizeAndMayOpenANewPage()) {
      writePageToPageBuffer();
    }
  }

  public void writeTime(long time) {
    timeChunkWriter.write(time);
  }

  public void write(TimeColumn timeColumn, Column[] valueColumns, int batchSize) {
    if (remainingPointsNumber < batchSize) {
      int pointsHasWritten = (int) remainingPointsNumber;
      batchWrite(timeColumn, valueColumns, pointsHasWritten, 0);
      batchWrite(timeColumn, valueColumns, batchSize - pointsHasWritten, pointsHasWritten);
    } else {
      batchWrite(timeColumn, valueColumns, batchSize, 0);
    }
  }

  private void batchWrite(
      TimeColumn timeColumn, Column[] valueColumns, int batchSize, int arrayOffset) {
    valueIndex = 0;
    long[] times = timeColumn.getTimes();

    for (Column column : valueColumns) {
      ValueChunkWriter chunkWriter = valueChunkWriterList.get(valueIndex++);
      TSDataType tsDataType = chunkWriter.getDataType();
      switch (tsDataType) {
        case TEXT:
        case BLOB:
        case STRING:
          chunkWriter.write(times, column.getBinaries(), column.isNull(), batchSize, arrayOffset);
          break;
        case DOUBLE:
          chunkWriter.write(times, column.getDoubles(), column.isNull(), batchSize, arrayOffset);
          break;
        case BOOLEAN:
          chunkWriter.write(times, column.getBooleans(), column.isNull(), batchSize, arrayOffset);
          break;
        case INT64:
        case TIMESTAMP:
          chunkWriter.write(times, column.getLongs(), column.isNull(), batchSize, arrayOffset);
          break;
        case INT32:
        case DATE:
          chunkWriter.write(times, column.getInts(), column.isNull(), batchSize, arrayOffset);
          break;
        case FLOAT:
          chunkWriter.write(times, column.getFloats(), column.isNull(), batchSize, arrayOffset);
          break;
        default:
          throw new UnsupportedOperationException("Unknown data type " + tsDataType);
      }
    }

    write(times, batchSize, arrayOffset);
  }

  public void write(long[] time, int batchSize, int arrayOffset) {
    valueIndex = 0;
    timeChunkWriter.write(time, batchSize, arrayOffset);
    if (checkPageSizeAndMayOpenANewPage()) {
      writePageToPageBuffer();
    }

    remainingPointsNumber = timeChunkWriter.getRemainingPointNumberForCurrentPage();
  }

  public void writeByColumn(long time, int value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void writeByColumn(long time, long value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void writeByColumn(long time, boolean value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void writeByColumn(long time, float value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void writeByColumn(long time, double value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void writeByColumn(long time, Binary value, boolean isNull) {
    valueChunkWriterList.get(valueIndex).write(time, value, isNull);
  }

  public void nextColumn() {
    valueIndex++;
  }

  /**
   * check occupied memory size, if it exceeds the PageSize threshold, construct a page and put it
   * to pageBuffer
   */
  protected boolean checkPageSizeAndMayOpenANewPage() {
    if (timeChunkWriter.checkPageSizeAndMayOpenANewPage()) {
      return true;
    }
    for (ValueChunkWriter writer : valueChunkWriterList) {
      if (writer.checkPageSizeAndMayOpenANewPage()) {
        return true;
      }
    }
    return false;
  }

  protected void writePageToPageBuffer() {
    timeChunkWriter.writePageToPageBuffer();
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      valueChunkWriter.writePageToPageBuffer();
    }
  }

  public void writePageHeaderAndDataIntoTimeBuff(ByteBuffer data, PageHeader header)
      throws PageException {
    timeChunkWriter.writePageHeaderAndDataIntoBuff(data, header);
  }

  public void writePageHeaderAndDataIntoValueBuff(
      ByteBuffer data, PageHeader header, int valueIndex) throws PageException {
    valueChunkWriterList.get(valueIndex).writePageHeaderAndDataIntoBuff(data, header);
  }

  @Override
  public void writeToFileWriter(TsFileIOWriter tsfileWriter) throws IOException {
    timeChunkWriter.writeToFileWriter(tsfileWriter);
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      valueChunkWriter.writeToFileWriter(tsfileWriter);
    }
  }

  @Override
  public long estimateMaxSeriesMemSize() {
    long estimateMaxSeriesMemSize = timeChunkWriter.estimateMaxSeriesMemSize();
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      estimateMaxSeriesMemSize += valueChunkWriter.estimateMaxSeriesMemSize();
    }
    return estimateMaxSeriesMemSize;
  }

  public long getSerializedChunkSize() {
    long currentChunkSize = timeChunkWriter.getCurrentChunkSize();
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      currentChunkSize += valueChunkWriter.getCurrentChunkSize();
    }
    return currentChunkSize;
  }

  @Override
  public void sealCurrentPage() {
    timeChunkWriter.sealCurrentPage();
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      valueChunkWriter.sealCurrentPage();
    }
  }

  public void sealCurrentTimePage() {
    timeChunkWriter.sealCurrentPage();
  }

  public void sealCurrentValuePage(int valueIndex) {
    valueChunkWriterList.get(valueIndex).sealCurrentPage();
  }

  @Override
  public void clearPageWriter() {
    timeChunkWriter.clearPageWriter();
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      valueChunkWriter.clearPageWriter();
    }
  }

  @Override
  public boolean checkIsChunkSizeOverThreshold(
      long size, long pointNum, boolean returnTrueIfChunkEmpty) {
    if ((returnTrueIfChunkEmpty && timeChunkWriter.getPointNum() == 0)
        || (timeChunkWriter.getPointNum() >= pointNum
            || timeChunkWriter.estimateMaxSeriesMemSize() >= size)) {
      return true;
    }
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      if (valueChunkWriter.estimateMaxSeriesMemSize() >= size) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isEmpty() {
    return timeChunkWriter.getPointNum() + timeChunkWriter.getPageWriter().getPointNumber() == 0;
  }

  @Override
  public boolean checkIsUnsealedPageOverThreshold(
      long size, long pointNum, boolean returnTrueIfPageEmpty) {
    if ((returnTrueIfPageEmpty && timeChunkWriter.getPageWriter().getPointNumber() == 0)
        || timeChunkWriter.checkIsUnsealedPageOverThreshold(size, pointNum)) {
      return true;
    }
    for (ValueChunkWriter valueChunkWriter : valueChunkWriterList) {
      if (valueChunkWriter.checkIsUnsealedPageOverThreshold(size)) {
        return true;
      }
    }
    return false;
  }

  public ValueChunkWriter getValueChunkWriterByIndex(int valueIndex) {
    return valueChunkWriterList.get(valueIndex);
  }

  /** Test only */
  public TimeChunkWriter getTimeChunkWriter() {
    return timeChunkWriter;
  }

  /** Test only */
  public List<ValueChunkWriter> getValueChunkWriterList() {
    return valueChunkWriterList;
  }
}

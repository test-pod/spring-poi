package com.shang.poi.config;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.alibaba.excel.util.DateUtils;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class TimestampStringConverter implements Converter<Timestamp> {

    @Override
    public Class<?> supportJavaTypeKey() {
        return Timestamp.class;
    }

    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    @Override
    public Timestamp convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty,
                                       GlobalConfiguration globalConfiguration) {
        if (contentProperty == null || contentProperty.getDateTimeFormatProperty() == null) {
            return Timestamp.valueOf(DateUtils.parseLocalDateTime(cellData.getStringValue(), null, globalConfiguration.getLocale()));
        } else {
            return Timestamp.valueOf(DateUtils.parseLocalDateTime(cellData.getStringValue(),
                    contentProperty.getDateTimeFormatProperty().getFormat(), globalConfiguration.getLocale()));
        }
    }

    @Override
    public WriteCellData<?> convertToExcelData(Timestamp value, ExcelContentProperty contentProperty,
                                               GlobalConfiguration globalConfiguration) {
        if (contentProperty == null || contentProperty.getDateTimeFormatProperty() == null) {
            return new WriteCellData<>(DateUtils.format(value.toLocalDateTime(), null, globalConfiguration.getLocale()));
        } else {
            return new WriteCellData<>(
                    DateUtils.format(value.toLocalDateTime(), contentProperty.getDateTimeFormatProperty().getFormat(),
                            globalConfiguration.getLocale()));
        }
    }

}

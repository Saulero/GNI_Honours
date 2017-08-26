package databeans;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author Saul
 */
public class MetaMethodData implements Serializable {

    private long days;
    private LocalDate beginDate;
    private LocalDate endDate;

    public MetaMethodData() {
    }

    public MetaMethodData(long days) {
        this.days = days;
    }

    public MetaMethodData(LocalDate beginDate, LocalDate endDate) {
        this.beginDate = beginDate;
        this.endDate = endDate;
    }

    public long getDays() {
        return days;
    }

    public void setDays(long days) {
        this.days = days;
    }

    public LocalDate getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(LocalDate beginDate) {
        this.beginDate = beginDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @Override
    public String toString() {
        return "MetaMethodData{" +
                "days=" + days +
                ", beginDate=" + beginDate +
                ", endDate=" + endDate +
                '}';
    }
}

package databeans;

import java.time.LocalDate;

/**
 * @author Saul
 */
public class SetValueRequest {

    private SetValueKey key;
    private double value;
    private LocalDate date;

    public SetValueRequest(final SetValueKey newKey, final double newValue, final LocalDate newDate) {
        this.key = newKey;
        this.value = newValue;
        this.date = newDate;
    }

    public SetValueKey getKey() {
        return key;
    }

    public void setKey(final SetValueKey newKey) {
        this.key = newKey;
    }

    public double getValue() {
        return value;
    }

    public void setValue(final double newValue) {
        this.value = newValue;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(final LocalDate newDate) {
        this.date = newDate;
    }
}

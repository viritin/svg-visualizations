package org.vaadin.svgvis.testdata;

import java.io.Serializable;
import java.time.Instant;

/**
 * Weather station data record for testing.
 * Contains readings from a personal weather station.
 */
public class RawWeatherStationData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String stationtype;
    private long runtime;
    private String passkey;
    private String dateutc;
    private double tempinf;
    private int humidityin;
    private double baromrelin;
    private double baromabsin;
    private double tempf;
    private int humidity;
    private int winddir;
    private double windspeedmph;
    private double windgustmph;
    private double maxdailygust;
    private double solarradiation;
    private int uv;
    private double rainratein;
    private double eventrainin;
    private double hourlyrainin;
    private double dailyrainin;
    private double weeklyrainin;
    private double monthlyrainin;
    private double yearlyrainin;
    private double totalrainin;
    private double temp3f;
    private double tf_ch1;
    private int wh65batt;
    private int wh25batt;
    private int batt3;
    private String freq;
    private String model;
    Instant instant;

    public String getStationtype() { return stationtype; }
    public void setStationtype(String stationtype) { this.stationtype = stationtype; }
    public long getRuntime() { return runtime; }
    public void setRuntime(long runtime) { this.runtime = runtime; }
    public String getPasskey() { return passkey; }
    public void setPasskey(String passkey) { this.passkey = passkey; }
    public String getDateutc() { return dateutc; }
    public void setDateutc(String dateutc) { this.dateutc = dateutc; }
    public double getTempinf() { return tempinf; }
    public void setTempinf(double tempinf) { this.tempinf = tempinf; }
    public int getHumidityin() { return humidityin; }
    public void setHumidityin(int humidityin) { this.humidityin = humidityin; }
    public double getBaromrelin() { return baromrelin; }
    public void setBaromrelin(double baromrelin) { this.baromrelin = baromrelin; }
    public double getBaromabsin() { return baromabsin; }
    public void setBaromabsin(double baromabsin) { this.baromabsin = baromabsin; }
    public double getTempf() { return tempf; }
    public void setTempf(double tempf) { this.tempf = tempf; }
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public int getWinddir() { return winddir; }
    public void setWinddir(int winddir) { this.winddir = winddir; }
    public double getWindspeedmph() { return windspeedmph; }
    public void setWindspeedmph(double windspeedmph) { this.windspeedmph = windspeedmph; }
    public double getWindgustmph() { return windgustmph; }
    public void setWindgustmph(double windgustmph) { this.windgustmph = windgustmph; }
    public double getMaxdailygust() { return maxdailygust; }
    public void setMaxdailygust(double maxdailygust) { this.maxdailygust = maxdailygust; }
    public double getSolarradiation() { return solarradiation; }
    public void setSolarradiation(double solarradiation) { this.solarradiation = solarradiation; }
    public int getUv() { return uv; }
    public void setUv(int uv) { this.uv = uv; }
    public double getRainratein() { return rainratein; }
    public void setRainratein(double rainratein) { this.rainratein = rainratein; }
    public double getEventrainin() { return eventrainin; }
    public void setEventrainin(double eventrainin) { this.eventrainin = eventrainin; }
    public double getHourlyrainin() { return hourlyrainin; }
    public void setHourlyrainin(double hourlyrainin) { this.hourlyrainin = hourlyrainin; }
    public double getDailyrainin() { return dailyrainin; }
    public void setDailyrainin(double dailyrainin) { this.dailyrainin = dailyrainin; }
    public double getWeeklyrainin() { return weeklyrainin; }
    public void setWeeklyrainin(double weeklyrainin) { this.weeklyrainin = weeklyrainin; }
    public double getMonthlyrainin() { return monthlyrainin; }
    public void setMonthlyrainin(double monthlyrainin) { this.monthlyrainin = monthlyrainin; }
    public double getYearlyrainin() { return yearlyrainin; }
    public void setYearlyrainin(double yearlyrainin) { this.yearlyrainin = yearlyrainin; }
    public double getTotalrainin() { return totalrainin; }
    public void setTotalrainin(double totalrainin) { this.totalrainin = totalrainin; }
    public double getTemp3f() { return temp3f; }
    public void setTemp3f(double temp3f) { this.temp3f = temp3f; }
    public double getTf_ch1() { return tf_ch1; }
    public void setTf_ch1(double tf_ch1) { this.tf_ch1 = tf_ch1; }
    public int getWh65batt() { return wh65batt; }
    public void setWh65batt(int wh65batt) { this.wh65batt = wh65batt; }
    public int getWh25batt() { return wh25batt; }
    public void setWh25batt(int wh25batt) { this.wh25batt = wh25batt; }
    public int getBatt3() { return batt3; }
    public void setBatt3(int batt3) { this.batt3 = batt3; }
    public String getFreq() { return freq; }
    public void setFreq(String freq) { this.freq = freq; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    // Conversion methods - temperature
    public double getTempInC() { return (tempinf - 32) * 5 / 9; }
    public double getTempfC() { return (tempf - 32) * 5 / 9; }
    public double getTemp3C() { return (temp3f - 32) * 5 / 9; }
    public double getSaunaC() { return (tf_ch1 - 32) * 5 / 9; }

    // Conversion methods - wind
    public double getWindSpeedKph() { return windspeedmph * 1.60934; }
    public double getWindSpeedMs() { return windspeedmph * 0.44704; }
    public double getWindGustMs() { return windgustmph * 0.44704; }
    public double getMaxDailyGustMs() { return maxdailygust * 0.44704; }

    // Conversion methods - rain
    public double getRainRateMm() { return rainratein * 25.4; }
    public double getDailyRainMm() { return dailyrainin * 25.4; }

    // Conversion methods - pressure
    public double getBaromRelHpa() { return baromrelin * 33.8639; }

    public Instant getInstant() {
        if (instant == null && dateutc != null) {
            instant = Instant.parse(dateutc.replace(" ", "T") + ".00Z");
        }
        return instant;
    }

    public boolean hasValidAirTemp() { return humidity != 0; }

    @Override
    public String toString() {
        return "WeatherData{dateutc=" + dateutc + ", tempC=" + String.format("%.1f", getTempfC()) + "}";
    }
}

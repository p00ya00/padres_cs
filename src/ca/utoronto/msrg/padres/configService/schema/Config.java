//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.01.22 at 03:51:15 PM CET 
//


package ca.utoronto.msrg.padres.configService.schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for config complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="config">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="topology" type="{}topology"/>
 *         &lt;element name="backup" type="{}backup"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
//@XmlType(name = "config", propOrder = {
//    "topology",
//    "backup"
//})
@XmlRootElement(name = "config")
public class Config {

    @XmlElement(required = true)
    protected Topology topology;
    @XmlElement(required = true)
    protected Backup backup;

    /**
     * Gets the value of the topology property.
     * 
     * @return
     *     possible object is
     *     {@link Topology }
     *     
     */
    public Topology getTopology() {
        return topology;
    }

    /**
     * Sets the value of the topology property.
     * 
     * @param value
     *     allowed object is
     *     {@link Topology }
     *     
     */
    public void setTopology(Topology value) {
        this.topology = value;
    }

    /**
     * Gets the value of the backup property.
     * 
     * @return
     *     possible object is
     *     {@link Backup }
     *     
     */
    public Backup getBackup() {
        return backup;
    }

    /**
     * Sets the value of the backup property.
     * 
     * @param value
     *     allowed object is
     *     {@link Backup }
     *     
     */
    public void setBackup(Backup value) {
        this.backup = value;
    }

}

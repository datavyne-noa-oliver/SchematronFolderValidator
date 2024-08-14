<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0" xmlns:svrl="http://purl.oclc.org/dsdl/svrl">
    <xsl:output method="text" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <!-- Template to match the root element -->
    <xsl:template match="/">
        <xsl:text>{</xsl:text>
        <xsl:text>&#10;  "errorCount": </xsl:text>
        <xsl:value-of select="count(//svrl:failed-assert[contains(following-sibling::svrl:fired-rule[1]/@id, 'errors') or not(contains(following-sibling::svrl:fired-rule[1]/@id, 'warnings'))])"/>
        <xsl:text>,&#10;  "errors": [</xsl:text>
        <xsl:for-each select="//svrl:failed-assert[contains(following-sibling::svrl:fired-rule[1]/@id, 'errors') or not(contains(following-sibling::svrl:fired-rule[1]/@id, 'warnings'))]">
            <xsl:if test="position() > 1">,</xsl:if>
            <xsl:text>&#10;    {</xsl:text>
            <xsl:text>&#10;      "assertionId": "</xsl:text>
            <xsl:choose>
                <xsl:when test="normalize-space(@id) != ''">
                    <xsl:value-of select="normalize-space(@id)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="substring-before(substring-after(normalize-space(svrl:text), 'CONF:'), ')')"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>",&#10;      "type": "error",&#10;      "description": "</xsl:text>
            <xsl:value-of select="normalize-space(svrl:text)"/>
            <xsl:text>",&#10;      "path": "</xsl:text>
            <xsl:value-of select="@location"/>
            <xsl:text>"&#10;    }</xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;  ],&#10;  "warningCount": </xsl:text>
        <xsl:value-of select="count(//svrl:failed-assert[contains(following-sibling::svrl:fired-rule[1]/@id, 'warnings')])"/>
        <xsl:text>,&#10;  "warnings": [</xsl:text>
        <xsl:for-each select="//svrl:failed-assert[contains(following-sibling::svrl:fired-rule[1]/@id, 'warnings')]">
            <xsl:if test="position() > 1">,</xsl:if>
            <xsl:text>&#10;    {</xsl:text>
            <xsl:text>&#10;      "assertionId": "</xsl:text>
            <xsl:choose>
                <xsl:when test="normalize-space(@id) != ''">
                    <xsl:value-of select="normalize-space(@id)"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="substring-before(substring-after(normalize-space(svrl:text), 'CONF:'), ')')"/>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:text>",&#10;      "type": "warning",&#10;      "description": "</xsl:text>
            <xsl:value-of select="normalize-space(svrl:text)"/>
            <xsl:text>",&#10;      "path": "</xsl:text>
            <xsl:value-of select="@location"/>
            <xsl:text>"&#10;    }</xsl:text>
        </xsl:for-each>
        <xsl:text>&#10;  ]&#10;}</xsl:text>
    </xsl:template>
</xsl:stylesheet>

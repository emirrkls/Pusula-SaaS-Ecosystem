# Read-only STARTTLS probe. No credentials, AUTH, MAIL FROM, RCPT TO or message body.
$ErrorActionPreference='Stop'
$smtpProbeHost='smtp.hostinger.com'
$smtpProbePort=587
$smtpProbeClient=[System.Net.Sockets.TcpClient]::new()
try {
    if(-not $smtpProbeClient.ConnectAsync($smtpProbeHost,$smtpProbePort).Wait(5000)){throw 'SMTP TCP connection timed out.'}
    $smtpProbeStream=$smtpProbeClient.GetStream()
    $smtpProbeStream.ReadTimeout=5000;$smtpProbeStream.WriteTimeout=5000
    $smtpProbeReader=[System.IO.StreamReader]::new($smtpProbeStream,[System.Text.Encoding]::ASCII,$false,1024,$true)
    $smtpProbeWriter=[System.IO.StreamWriter]::new($smtpProbeStream,[System.Text.Encoding]::ASCII,1024,$true)
    $smtpProbeWriter.NewLine="`r`n";$smtpProbeWriter.AutoFlush=$true
    function Read-SmtpProbeReply([string]$expectedCode) {
        $smtpReplyLines=[System.Collections.Generic.List[string]]::new()
        do {
            $smtpReplyLine=$smtpProbeReader.ReadLine()
            if($null -eq $smtpReplyLine -or $smtpReplyLine.Length -lt 4 -or -not $smtpReplyLine.StartsWith($expectedCode)){throw "Unexpected SMTP reply; expected $expectedCode."}
            $smtpReplyLines.Add($smtpReplyLine)
            if($smtpReplyLines.Count -gt 30){throw 'Unbounded SMTP reply.'}
        }while($smtpReplyLine[3] -eq '-')
        return $smtpReplyLines
    }
    $null=Read-SmtpProbeReply '220'
    $smtpProbeWriter.WriteLine('EHLO localhost')
    $smtpCapabilities=Read-SmtpProbeReply '250'
    if(-not ($smtpCapabilities -match 'STARTTLS')){throw 'SMTP endpoint does not advertise STARTTLS.'}
    $smtpProbeWriter.WriteLine('STARTTLS');$null=Read-SmtpProbeReply '220'
    $smtpProbeReader.Dispose();$smtpProbeWriter.Dispose()
    # No validation callback: platform hostname/trust validation stays enabled.
    $smtpProbeTls=[System.Net.Security.SslStream]::new($smtpProbeStream,$false)
    try {
        $smtpProbeOptions=[System.Net.Security.SslClientAuthenticationOptions]::new()
        $smtpProbeOptions.TargetHost=$smtpProbeHost
        $smtpProbeOptions.CertificateRevocationCheckMode=[System.Security.Cryptography.X509Certificates.X509RevocationMode]::Online
        if(-not $smtpProbeTls.AuthenticateAsClientAsync($smtpProbeOptions).Wait(10000)){throw 'SMTP TLS handshake timed out.'}
        [pscustomobject]@{Host=$smtpProbeHost;Port=$smtpProbePort;StartTls=$true;TlsProtocol=$smtpProbeTls.SslProtocol.ToString();CertificateValidated=$smtpProbeTls.IsAuthenticated;CredentialUsed=$false;MessageSent=$false}|ConvertTo-Json
    }finally{$smtpProbeTls.Dispose()}
}finally{$smtpProbeClient.Dispose()}

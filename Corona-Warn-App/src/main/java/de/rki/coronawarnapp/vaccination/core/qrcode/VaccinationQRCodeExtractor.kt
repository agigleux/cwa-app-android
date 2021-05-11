package de.rki.coronawarnapp.vaccination.core.qrcode

import de.rki.coronawarnapp.coronatest.qrcode.QrCodeExtractor
import de.rki.coronawarnapp.util.compression.ZLIBDecompressor
import de.rki.coronawarnapp.util.encoding.decodeBase45
import de.rki.coronawarnapp.vaccination.core.certificate.RawCOSEObject
import de.rki.coronawarnapp.vaccination.core.qrcode.InvalidHealthCertificateException.ErrorCode.HC_BASE45_DECODING_FAILED
import de.rki.coronawarnapp.vaccination.core.qrcode.InvalidHealthCertificateException.ErrorCode.HC_ZLIB_DECOMPRESSION_FAILED
import okio.ByteString
import timber.log.Timber
import javax.inject.Inject

class VaccinationQRCodeExtractor @Inject constructor(
    private val zLIBDecompressor: ZLIBDecompressor,
    private val vaccinationCertificateCOSEParser: VaccinationCertificateCOSEParser,
) : QrCodeExtractor<VaccinationCertificateQRCode> {

    override fun canHandle(rawString: String): Boolean = rawString.startsWith(PREFIX)

    override fun extract(rawString: String): VaccinationCertificateQRCode {
        val rawCOSEObject = rawString
            .removePrefix(PREFIX)
            .tryDecodeBase45()
            .decompress()

        return VaccinationCertificateQRCode(
            parsedData = vaccinationCertificateCOSEParser.parse(rawCOSEObject),
            certificateCOSE = rawCOSEObject,
        )
    }

    private fun String.tryDecodeBase45(): ByteString = try {
        this.decodeBase45()
    } catch (e: Exception) {
        Timber.e(e)
        throw InvalidHealthCertificateException(HC_BASE45_DECODING_FAILED)
    }

    private fun ByteString.decompress(): RawCOSEObject = try {
        RawCOSEObject(zLIBDecompressor.decode(this.toByteArray()))
    } catch (e: Exception) {
        Timber.e(e)
        throw InvalidHealthCertificateException(HC_ZLIB_DECOMPRESSION_FAILED)
    }

    companion object {
        private const val PREFIX = "HC1:"
    }
}

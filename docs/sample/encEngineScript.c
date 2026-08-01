#include <jni.h>

#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdlib.h>
#include <android/log.h>

// Enable ECB, CTR and CBC mode. Note this can be done before including aes.h or at compile-time.
// E.g. with GCC by using the -D flag: gcc -c aes.c -DCBC=0 -DCTR=1 -DECB=1
#define CBC 1
#define CTR 1
#define ECB 1

#include "aes.h"

JNIEnv *Jenv;
jclass JthisClass;

//static void phex(uint8_t *str);
//
//static int test_encrypt_cbc(void);
//
//static int test_decrypt_cbc(void);
//
//static int test_encrypt_ctr(void);
//
//static int test_decrypt_ctr(void);
//
//static int test_encrypt_ecb(void);
//
//static int test_decrypt_ecb(void);
//
//static void test_encrypt_ecb_verbose(void);
//
//int tests() {
//    int exit;
//
//#if defined(AES256)
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\nTesting AES256\n\n");
//#elif defined(AES192)
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\nTesting AES192\n\n");
//#elif defined(AES128)
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\nTesting AES128\n\n");
//#else
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "You need to specify a symbol between AES128, AES192 or AES256. Exiting");
//    return 0;
//#endif
//
//    exit = test_encrypt_cbc() + test_decrypt_cbc() +
//           test_encrypt_ctr() + test_decrypt_ctr() +
//           test_decrypt_ecb() + test_encrypt_ecb();
//    test_encrypt_ecb_verbose();
//
//    return exit;
//}
//
//
//// prints string as hex
//static void phex(uint8_t *str) {
//
//#if defined(AES256)
//    uint8_t len = 32;
//#elif defined(AES192)
//    uint8_t len = 24;
//#elif defined(AES128)
//    uint8_t len = 16;
//#endif
//
//    unsigned char i;
//    for (i = 0; i < len; ++i)
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "%.2x", str[i]);
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\n");
//}
//
//static void test_encrypt_ecb_verbose(void) {
//    // Example of more verbose verification
//
//    uint8_t i;
//
//    // 128bit key
//    uint8_t key[16] = {(uint8_t) 0x2b, (uint8_t) 0x7e, (uint8_t) 0x15, (uint8_t) 0x16,
//                       (uint8_t) 0x28, (uint8_t) 0xae, (uint8_t) 0xd2, (uint8_t) 0xa6,
//                       (uint8_t) 0xab, (uint8_t) 0xf7, (uint8_t) 0x15, (uint8_t) 0x88,
//                       (uint8_t) 0x09, (uint8_t) 0xcf, (uint8_t) 0x4f, (uint8_t) 0x3c};
//    // 512bit text
//    uint8_t plain_text[64] = {(uint8_t) 0x6b, (uint8_t) 0xc1, (uint8_t) 0xbe, (uint8_t) 0xe2,
//                              (uint8_t) 0x2e, (uint8_t) 0x40, (uint8_t) 0x9f, (uint8_t) 0x96,
//                              (uint8_t) 0xe9, (uint8_t) 0x3d, (uint8_t) 0x7e, (uint8_t) 0x11,
//                              (uint8_t) 0x73, (uint8_t) 0x93, (uint8_t) 0x17, (uint8_t) 0x2a,
//                              (uint8_t) 0xae, (uint8_t) 0x2d, (uint8_t) 0x8a, (uint8_t) 0x57,
//                              (uint8_t) 0x1e, (uint8_t) 0x03, (uint8_t) 0xac, (uint8_t) 0x9c,
//                              (uint8_t) 0x9e, (uint8_t) 0xb7, (uint8_t) 0x6f, (uint8_t) 0xac,
//                              (uint8_t) 0x45, (uint8_t) 0xaf, (uint8_t) 0x8e, (uint8_t) 0x51,
//                              (uint8_t) 0x30, (uint8_t) 0xc8, (uint8_t) 0x1c, (uint8_t) 0x46,
//                              (uint8_t) 0xa3, (uint8_t) 0x5c, (uint8_t) 0xe4, (uint8_t) 0x11,
//                              (uint8_t) 0xe5, (uint8_t) 0xfb, (uint8_t) 0xc1, (uint8_t) 0x19,
//                              (uint8_t) 0x1a, (uint8_t) 0x0a, (uint8_t) 0x52, (uint8_t) 0xef,
//                              (uint8_t) 0xf6, (uint8_t) 0x9f, (uint8_t) 0x24, (uint8_t) 0x45,
//                              (uint8_t) 0xdf, (uint8_t) 0x4f, (uint8_t) 0x9b, (uint8_t) 0x17,
//                              (uint8_t) 0xad, (uint8_t) 0x2b, (uint8_t) 0x41, (uint8_t) 0x7b,
//                              (uint8_t) 0xe6, (uint8_t) 0x6c, (uint8_t) 0x37, (uint8_t) 0x10};
//
//    // print text to encrypt, key and IV
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "ECB encrypt verbose:\n\n");
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "plain text:\n");
//    for (i = (uint8_t) 0; i < (uint8_t) 4; ++i) {
//        phex(plain_text + i * (uint8_t) 16);
//    }
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\n");
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "key:\n");
//    phex(key);
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\n");
//
//    // print the resulting cipher as 4 x 16 byte strings
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "ciphertext:\n");
//
//    struct AES_ctx ctx;
//    AES_init_ctx(&ctx, key);
//
//    for (i = 0; i < 4; ++i) {
//        AES_ECB_encrypt(&ctx, plain_text + (i * 16));
//        phex(plain_text + (i * 16));
//    }
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "\n");
//}
//
//static int test_encrypt_ecb(void) {
//#if defined(AES256)
//    uint8_t key[] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
//                      0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };
//    uint8_t out[] = { 0xf3, 0xee, 0xd1, 0xbd, 0xb5, 0xd2, 0xa0, 0x3c, 0x06, 0x4b, 0x5a, 0x7e, 0x3d, 0xb1, 0x81, 0xf8 };
//#elif defined(AES192)
//    uint8_t key[] = { 0x8e, 0x73, 0xb0, 0xf7, 0xda, 0x0e, 0x64, 0x52, 0xc8, 0x10, 0xf3, 0x2b, 0x80, 0x90, 0x79, 0xe5,
//                      0x62, 0xf8, 0xea, 0xd2, 0x52, 0x2c, 0x6b, 0x7b };
//    uint8_t out[] = { 0xbd, 0x33, 0x4f, 0x1d, 0x6e, 0x45, 0xf2, 0x5f, 0xf7, 0x12, 0xa2, 0x14, 0x57, 0x1f, 0xa5, 0xcc };
//#elif defined(AES128)
//    uint8_t key[] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                     0xcf, 0x4f, 0x3c};
//    uint8_t out[] = {0x3a, 0xd7, 0x7b, 0xb4, 0x0d, 0x7a, 0x36, 0x60, 0xa8, 0x9e, 0xca, 0xf3, 0x24,
//                     0x66, 0xef, 0x97};
//#endif
//
//    uint8_t in[] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                    0x93, 0x17, 0x2a};
//    struct AES_ctx ctx;
//
//    AES_init_ctx(&ctx, key);
//    AES_ECB_encrypt(&ctx, in);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "ECB encrypt: ");
//
//    if (0 == memcmp((char *) out, (char *) in, 16)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//}
//
//static int test_decrypt_cbc(void) {
//
//#if defined(AES256)
//    uint8_t key[] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
//                      0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };
//    uint8_t in[]  = { 0xf5, 0x8c, 0x4c, 0x04, 0xd6, 0xe5, 0xf1, 0xba, 0x77, 0x9e, 0xab, 0xfb, 0x5f, 0x7b, 0xfb, 0xd6,
//                      0x9c, 0xfc, 0x4e, 0x96, 0x7e, 0xdb, 0x80, 0x8d, 0x67, 0x9f, 0x77, 0x7b, 0xc6, 0x70, 0x2c, 0x7d,
//                      0x39, 0xf2, 0x33, 0x69, 0xa9, 0xd9, 0xba, 0xcf, 0xa5, 0x30, 0xe2, 0x63, 0x04, 0x23, 0x14, 0x61,
//                      0xb2, 0xeb, 0x05, 0xe2, 0xc3, 0x9b, 0xe9, 0xfc, 0xda, 0x6c, 0x19, 0x07, 0x8c, 0x6a, 0x9d, 0x1b };
//#elif defined(AES192)
//    uint8_t key[] = { 0x8e, 0x73, 0xb0, 0xf7, 0xda, 0x0e, 0x64, 0x52, 0xc8, 0x10, 0xf3, 0x2b, 0x80, 0x90, 0x79, 0xe5, 0x62, 0xf8, 0xea, 0xd2, 0x52, 0x2c, 0x6b, 0x7b };
//    uint8_t in[]  = { 0x4f, 0x02, 0x1d, 0xb2, 0x43, 0xbc, 0x63, 0x3d, 0x71, 0x78, 0x18, 0x3a, 0x9f, 0xa0, 0x71, 0xe8,
//                      0xb4, 0xd9, 0xad, 0xa9, 0xad, 0x7d, 0xed, 0xf4, 0xe5, 0xe7, 0x38, 0x76, 0x3f, 0x69, 0x14, 0x5a,
//                      0x57, 0x1b, 0x24, 0x20, 0x12, 0xfb, 0x7a, 0xe0, 0x7f, 0xa9, 0xba, 0xac, 0x3d, 0xf1, 0x02, 0xe0,
//                      0x08, 0xb0, 0xe2, 0x79, 0x88, 0x59, 0x88, 0x81, 0xd9, 0x20, 0xa9, 0xe6, 0x4f, 0x56, 0x15, 0xcd };
//#elif defined(AES128)
//    uint8_t key[] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                     0xcf, 0x4f, 0x3c};
//    uint8_t in[] = {0x76, 0x49, 0xab, 0xac, 0x81, 0x19, 0xb2, 0x46, 0xce, 0xe9, 0x8e, 0x9b, 0x12,
//                    0xe9, 0x19, 0x7d,
//                    0x50, 0x86, 0xcb, 0x9b, 0x50, 0x72, 0x19, 0xee, 0x95, 0xdb, 0x11, 0x3a, 0x91,
//                    0x76, 0x78, 0xb2,
//                    0x73, 0xbe, 0xd6, 0xb8, 0xe3, 0xc1, 0x74, 0x3b, 0x71, 0x16, 0xe6, 0x9e, 0x22,
//                    0x22, 0x95, 0x16,
//                    0x3f, 0xf1, 0xca, 0xa1, 0x68, 0x1f, 0xac, 0x09, 0x12, 0x0e, 0xca, 0x30, 0x75,
//                    0x86, 0xe1, 0xa7};
//#endif
//    uint8_t iv[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
//                    0x0d, 0x0e, 0x0f};
//    uint8_t out[] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                     0x93, 0x17, 0x2a,
//                     0xae, 0x2d, 0x8a, 0x57, 0x1e, 0x03, 0xac, 0x9c, 0x9e, 0xb7, 0x6f, 0xac, 0x45,
//                     0xaf, 0x8e, 0x51,
//                     0x30, 0xc8, 0x1c, 0x46, 0xa3, 0x5c, 0xe4, 0x11, 0xe5, 0xfb, 0xc1, 0x19, 0x1a,
//                     0x0a, 0x52, 0xef,
//                     0xf6, 0x9f, 0x24, 0x45, 0xdf, 0x4f, 0x9b, 0x17, 0xad, 0x2b, 0x41, 0x7b, 0xe6,
//                     0x6c, 0x37, 0x10};
////  uint8_t buffer[64];
//    struct AES_ctx ctx;
//
//    AES_init_ctx_iv(&ctx, key, iv);
//    AES_CBC_decrypt_buffer(&ctx, in, 64);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "CBC decrypt: ");
//
//    if (0 == memcmp((char *) out, (char *) in, 64)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//}
//
//static int test_encrypt_cbc(void) {
//#if defined(AES256)
//    uint8_t key[] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
//                      0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };
//    uint8_t out[] = { 0xf5, 0x8c, 0x4c, 0x04, 0xd6, 0xe5, 0xf1, 0xba, 0x77, 0x9e, 0xab, 0xfb, 0x5f, 0x7b, 0xfb, 0xd6,
//                      0x9c, 0xfc, 0x4e, 0x96, 0x7e, 0xdb, 0x80, 0x8d, 0x67, 0x9f, 0x77, 0x7b, 0xc6, 0x70, 0x2c, 0x7d,
//                      0x39, 0xf2, 0x33, 0x69, 0xa9, 0xd9, 0xba, 0xcf, 0xa5, 0x30, 0xe2, 0x63, 0x04, 0x23, 0x14, 0x61,
//                      0xb2, 0xeb, 0x05, 0xe2, 0xc3, 0x9b, 0xe9, 0xfc, 0xda, 0x6c, 0x19, 0x07, 0x8c, 0x6a, 0x9d, 0x1b };
//#elif defined(AES192)
//    uint8_t key[] = { 0x8e, 0x73, 0xb0, 0xf7, 0xda, 0x0e, 0x64, 0x52, 0xc8, 0x10, 0xf3, 0x2b, 0x80, 0x90, 0x79, 0xe5, 0x62, 0xf8, 0xea, 0xd2, 0x52, 0x2c, 0x6b, 0x7b };
//    uint8_t out[] = { 0x4f, 0x02, 0x1d, 0xb2, 0x43, 0xbc, 0x63, 0x3d, 0x71, 0x78, 0x18, 0x3a, 0x9f, 0xa0, 0x71, 0xe8,
//                      0xb4, 0xd9, 0xad, 0xa9, 0xad, 0x7d, 0xed, 0xf4, 0xe5, 0xe7, 0x38, 0x76, 0x3f, 0x69, 0x14, 0x5a,
//                      0x57, 0x1b, 0x24, 0x20, 0x12, 0xfb, 0x7a, 0xe0, 0x7f, 0xa9, 0xba, 0xac, 0x3d, 0xf1, 0x02, 0xe0,
//                      0x08, 0xb0, 0xe2, 0x79, 0x88, 0x59, 0x88, 0x81, 0xd9, 0x20, 0xa9, 0xe6, 0x4f, 0x56, 0x15, 0xcd };
//#elif defined(AES128)
//    uint8_t key[] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                     0xcf, 0x4f, 0x3c};
//    uint8_t out[] = {0x76, 0x49, 0xab, 0xac, 0x81, 0x19, 0xb2, 0x46, 0xce, 0xe9, 0x8e, 0x9b, 0x12,
//                     0xe9, 0x19, 0x7d,
//                     0x50, 0x86, 0xcb, 0x9b, 0x50, 0x72, 0x19, 0xee, 0x95, 0xdb, 0x11, 0x3a, 0x91,
//                     0x76, 0x78, 0xb2,
//                     0x73, 0xbe, 0xd6, 0xb8, 0xe3, 0xc1, 0x74, 0x3b, 0x71, 0x16, 0xe6, 0x9e, 0x22,
//                     0x22, 0x95, 0x16,
//                     0x3f, 0xf1, 0xca, 0xa1, 0x68, 0x1f, 0xac, 0x09, 0x12, 0x0e, 0xca, 0x30, 0x75,
//                     0x86, 0xe1, 0xa7};
//#endif
//    uint8_t iv[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
//                    0x0d, 0x0e, 0x0f};
//    uint8_t in[] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                    0x93, 0x17, 0x2a,
//                    0xae, 0x2d, 0x8a, 0x57, 0x1e, 0x03, 0xac, 0x9c, 0x9e, 0xb7, 0x6f, 0xac, 0x45,
//                    0xaf, 0x8e, 0x51,
//                    0x30, 0xc8, 0x1c, 0x46, 0xa3, 0x5c, 0xe4, 0x11, 0xe5, 0xfb, 0xc1, 0x19, 0x1a,
//                    0x0a, 0x52, 0xef,
//                    0xf6, 0x9f, 0x24, 0x45, 0xdf, 0x4f, 0x9b, 0x17, 0xad, 0x2b, 0x41, 0x7b, 0xe6,
//                    0x6c, 0x37, 0x10};
//    struct AES_ctx ctx;
//
//    AES_init_ctx_iv(&ctx, key, iv);
//    AES_CBC_encrypt_buffer(&ctx, in, 64);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "CBC encrypt: ");
//
//    if (0 == memcmp((char *) out, (char *) in, 64)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//}
//
//static int test_xcrypt_ctr(const char *xcrypt);
//
//static int test_encrypt_ctr(void) {
//    return test_xcrypt_ctr("encrypt");
//}
//
//static int test_decrypt_ctr(void) {
//    return test_xcrypt_ctr("decrypt");
//}
//
//static int test_xcrypt_ctr(const char *xcrypt) {
//#if defined(AES256)
//    uint8_t key[32] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
//                        0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };
//    uint8_t in[64]  = { 0x60, 0x1e, 0xc3, 0x13, 0x77, 0x57, 0x89, 0xa5, 0xb7, 0xa7, 0xf5, 0x04, 0xbb, 0xf3, 0xd2, 0x28,
//                        0xf4, 0x43, 0xe3, 0xca, 0x4d, 0x62, 0xb5, 0x9a, 0xca, 0x84, 0xe9, 0x90, 0xca, 0xca, 0xf5, 0xc5,
//                        0x2b, 0x09, 0x30, 0xda, 0xa2, 0x3d, 0xe9, 0x4c, 0xe8, 0x70, 0x17, 0xba, 0x2d, 0x84, 0x98, 0x8d,
//                        0xdf, 0xc9, 0xc5, 0x8d, 0xb6, 0x7a, 0xad, 0xa6, 0x13, 0xc2, 0xdd, 0x08, 0x45, 0x79, 0x41, 0xa6 };
//#elif defined(AES192)
//    uint8_t key[24] = { 0x8e, 0x73, 0xb0, 0xf7, 0xda, 0x0e, 0x64, 0x52, 0xc8, 0x10, 0xf3, 0x2b, 0x80, 0x90, 0x79, 0xe5,
//                        0x62, 0xf8, 0xea, 0xd2, 0x52, 0x2c, 0x6b, 0x7b };
//    uint8_t in[64]  = { 0x1a, 0xbc, 0x93, 0x24, 0x17, 0x52, 0x1c, 0xa2, 0x4f, 0x2b, 0x04, 0x59, 0xfe, 0x7e, 0x6e, 0x0b,
//                        0x09, 0x03, 0x39, 0xec, 0x0a, 0xa6, 0xfa, 0xef, 0xd5, 0xcc, 0xc2, 0xc6, 0xf4, 0xce, 0x8e, 0x94,
//                        0x1e, 0x36, 0xb2, 0x6b, 0xd1, 0xeb, 0xc6, 0x70, 0xd1, 0xbd, 0x1d, 0x66, 0x56, 0x20, 0xab, 0xf7,
//                        0x4f, 0x78, 0xa7, 0xf6, 0xd2, 0x98, 0x09, 0x58, 0x5a, 0x97, 0xda, 0xec, 0x58, 0xc6, 0xb0, 0x50 };
//#elif defined(AES128)
//    uint8_t key[16] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                       0xcf, 0x4f, 0x3c};
//    uint8_t in[64] = {0x87, 0x4d, 0x61, 0x91, 0xb6, 0x20, 0xe3, 0x26, 0x1b, 0xef, 0x68, 0x64, 0x99,
//                      0x0d, 0xb6, 0xce,
//                      0x98, 0x06, 0xf6, 0x6b, 0x79, 0x70, 0xfd, 0xff, 0x86, 0x17, 0x18, 0x7b, 0xb9,
//                      0xff, 0xfd, 0xff,
//                      0x5a, 0xe4, 0xdf, 0x3e, 0xdb, 0xd5, 0xd3, 0x5e, 0x5b, 0x4f, 0x09, 0x02, 0x0d,
//                      0xb0, 0x3e, 0xab,
//                      0x1e, 0x03, 0x1d, 0xda, 0x2f, 0xbe, 0x03, 0xd1, 0x79, 0x21, 0x70, 0xa0, 0xf3,
//                      0x00, 0x9c, 0xee};
//#endif
//    uint8_t iv[16] = {0xf0, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xfb, 0xfc,
//                      0xfd, 0xfe, 0xff};
//    uint8_t out[64] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                       0x93, 0x17, 0x2a,
//                       0xae, 0x2d, 0x8a, 0x57, 0x1e, 0x03, 0xac, 0x9c, 0x9e, 0xb7, 0x6f, 0xac, 0x45,
//                       0xaf, 0x8e, 0x51,
//                       0x30, 0xc8, 0x1c, 0x46, 0xa3, 0x5c, 0xe4, 0x11, 0xe5, 0xfb, 0xc1, 0x19, 0x1a,
//                       0x0a, 0x52, 0xef,
//                       0xf6, 0x9f, 0x24, 0x45, 0xdf, 0x4f, 0x9b, 0x17, 0xad, 0x2b, 0x41, 0x7b, 0xe6,
//                       0x6c, 0x37, 0x10};
//    struct AES_ctx ctx;
//
//    AES_init_ctx_iv(&ctx, key, iv);
//    AES_CTR_xcrypt_buffer(&ctx, in, 64);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "CTR %s: ", xcrypt);
//
//    if (0 == memcmp((char *) out, (char *) in, 64)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//}
//
//static int test_decrypt_ecb(void) {
//#if defined(AES256)
//    uint8_t key[] = { 0x60, 0x3d, 0xeb, 0x10, 0x15, 0xca, 0x71, 0xbe, 0x2b, 0x73, 0xae, 0xf0, 0x85, 0x7d, 0x77, 0x81,
//                      0x1f, 0x35, 0x2c, 0x07, 0x3b, 0x61, 0x08, 0xd7, 0x2d, 0x98, 0x10, 0xa3, 0x09, 0x14, 0xdf, 0xf4 };
//    uint8_t in[]  = { 0xf3, 0xee, 0xd1, 0xbd, 0xb5, 0xd2, 0xa0, 0x3c, 0x06, 0x4b, 0x5a, 0x7e, 0x3d, 0xb1, 0x81, 0xf8 };
//#elif defined(AES192)
//    uint8_t key[] = { 0x8e, 0x73, 0xb0, 0xf7, 0xda, 0x0e, 0x64, 0x52, 0xc8, 0x10, 0xf3, 0x2b, 0x80, 0x90, 0x79, 0xe5,
//                      0x62, 0xf8, 0xea, 0xd2, 0x52, 0x2c, 0x6b, 0x7b };
//    uint8_t in[]  = { 0xbd, 0x33, 0x4f, 0x1d, 0x6e, 0x45, 0xf2, 0x5f, 0xf7, 0x12, 0xa2, 0x14, 0x57, 0x1f, 0xa5, 0xcc };
//#elif defined(AES128)
//    uint8_t key[] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                     0xcf, 0x4f, 0x3c};
//    uint8_t in[] = {0x3a, 0xd7, 0x7b, 0xb4, 0x0d, 0x7a, 0x36, 0x60, 0xa8, 0x9e, 0xca, 0xf3, 0x24,
//                    0x66, 0xef, 0x97};
//#endif
//
//    uint8_t out[] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                     0x93, 0x17, 0x2a};
//    struct AES_ctx ctx;
//
//    AES_init_ctx(&ctx, key);
//    AES_ECB_decrypt(&ctx, in);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "ECB decrypt: ");
//
//    if (0 == memcmp((char *) out, (char *) in, 16)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//}
//
//char* encCBC(char *hexString) {
//#elif defined(AES128)
//    uint8_t key[] = {0x2b, 0x7e, 0x15, 0x16, 0x28, 0xae, 0xd2, 0xa6, 0xab, 0xf7, 0x15, 0x88, 0x09,
//                     0xcf, 0x4f, 0x3c};
//    uint8_t out[] = {0x76, 0x49, 0xab, 0xac, 0x81, 0x19, 0xb2, 0x46, 0xce, 0xe9, 0x8e, 0x9b, 0x12,
//                     0xe9, 0x19, 0x7d,
//                     0x50, 0x86, 0xcb, 0x9b, 0x50, 0x72, 0x19, 0xee, 0x95, 0xdb, 0x11, 0x3a, 0x91,
//                     0x76, 0x78, 0xb2,
//                     0x73, 0xbe, 0xd6, 0xb8, 0xe3, 0xc1, 0x74, 0x3b, 0x71, 0x16, 0xe6, 0x9e, 0x22,
//                     0x22, 0x95, 0x16,
//                     0x3f, 0xf1, 0xca, 0xa1, 0x68, 0x1f, 0xac, 0x09, 0x12, 0x0e, 0xca, 0x30, 0x75,
//                     0x86, 0xe1, 0xa7};
//#endif
//    uint8_t iv[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c,
//                    0x0d, 0x0e, 0x0f};
//    uint8_t in[] = {0x6b, 0xc1, 0xbe, 0xe2, 0x2e, 0x40, 0x9f, 0x96, 0xe9, 0x3d, 0x7e, 0x11, 0x73,
//                    0x93, 0x17, 0x2a,
//                    0xae, 0x2d, 0x8a, 0x57, 0x1e, 0x03, 0xac, 0x9c, 0x9e, 0xb7, 0x6f, 0xac, 0x45,
//                    0xaf, 0x8e, 0x51,
//                    0x30, 0xc8, 0x1c, 0x46, 0xa3, 0x5c, 0xe4, 0x11, 0xe5, 0xfb, 0xc1, 0x19, 0x1a,
//                    0x0a, 0x52, 0xef,
//                    0xf6, 0x9f, 0x24, 0x45, 0xdf, 0x4f, 0x9b, 0x17, 0xad, 0x2b, 0x41, 0x7b, 0xe6,
//                    0x6c, 0x37, 0x10};
//    struct AES_ctx ctx;
//
//    AES_init_ctx_iv(&ctx, key, iv);
//    AES_CBC_encrypt_buffer(&ctx, in, 64);
//
//    __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "CBC encrypt: ");
//
//    if (0 == memcmp((char *) out, (char *) in, 64)) {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "SUCCESS!\n");
//        return (0);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "MyLib", "FAILURE!\n");
//        return (1);
//    }
//    return asciiString;
//}
//
//char *decCBC(char *hexString) {
//    int length = strlen(hexString);
//    char *asciiString = malloc(length / 2 + 1);
//    for (int i = 0, j = 0; i < length; i += 2, j++) {
//        char hex[3] = {hexString[i], hexString[i + 1], '\0'};
//        asciiString[j] = strtol(hex, NULL, 16);
//    }
//    asciiString[length / 2] = '\0';
//    return asciiString;
//}
//
////extern "C" { if you need to use C++ instead of C, replace aes.h with aes.hpp https://github.com/kokke/tiny-AES-c/blob/master/aes.hpp
//// also rename test-aes.c to test-aes.cpp, and update it in CMakeLists.txt
//JNIEXPORT jstring JNICALL
//Java_com_signal_app_external_security_EncEngine_encEngineScript(JNIEnv *env, jobject obj,
//                                                                jstring in) {
//    Jenv = env;
//    const char *inCStr = (*env)->GetStringUTFChars(env, in, NULL);
//    JthisClass = (*env)->GetObjectClass(env, obj);
//    __android_log_print(ANDROID_LOG_DEBUG, "Libs|Enc", "%s", inCStr);
//    __android_log_print(ANDROID_LOG_DEBUG, "Libs|Enc", "%s", "SUCCESS");
//    return (*env)->NewStringUTF(env, hexString2Ascii(inCStr));
//}
//
//JNIEXPORT jstring JNICALL
//Java_com_signal_app_external_security_EncEngine_decEngineScript(JNIEnv *env, jobject obj,
//                                                                jstring in) {
//    Jenv = env;
//    const char *inCStr = (*env)->GetStringUTFChars(env, in, NULL);
//    JthisClass = (*env)->GetObjectClass(env, obj);
//    __android_log_print(ANDROID_LOG_DEBUG, "Libs|Dec", "%s", inCStr);
//    __android_log_print(ANDROID_LOG_DEBUG, "Libs|Dec", "%s", "SUCCESS");
//    return (*env)->NewStringUTF(env, hexString2Ascii(inCStr));
//}

const char b64_alphabet[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                            "abcdefghijklmnopqrstuvwxyz"
                            "0123456789+/";

// Internal Buffer
char buf[128];

int encodedLength(int plainLength) {
    int n = plainLength;
    return (n + 2 - ((n + 2) % 3)) / 3 * 4;
}

int decodedLength(char *input, int inputLength) {
    int i = 0;
    int numEq = 0;
    for (i = inputLength - 1; input[i] == '='; i--) {
        numEq++;
    }

    return ((6 * inputLength) / 8) - numEq;
}

//Private utility functions
void fromA3ToA4(unsigned char *A4, unsigned char *A3) {
    A4[0] = (A3[0] & 0xfc) >> 2;
    A4[1] = ((A3[0] & 0x03) << 4) + ((A3[1] & 0xf0) >> 4);
    A4[2] = ((A3[1] & 0x0f) << 2) + ((A3[2] & 0xc0) >> 6);
    A4[3] = (A3[2] & 0x3f);
}

void fromA4ToA3(unsigned char *A3, unsigned char *A4) {
    A3[0] = (A4[0] << 2) + ((A4[1] & 0x30) >> 4);
    A3[1] = ((A4[1] & 0xf) << 4) + ((A4[2] & 0x3c) >> 2);
    A3[2] = ((A4[2] & 0x3) << 6) + A4[3];
}

unsigned char lookupTable(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 71;
    if (c >= '0' && c <= '9') return c + 4;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

int encode(unsigned char *output, char *input, int inputLen) {
    int i = 0, j = 0;
    int encLen = 0;
    unsigned char a3[3];
    unsigned char a4[4];

    while (inputLen--) {
        a3[i++] = *(input++);
        if (i == 3) {
            fromA3ToA4(a4, a3);

            for (i = 0; i < 4; i++) {
                output[encLen++] = b64_alphabet[a4[i]];
            }

            i = 0;
        }
    }

    if (i) {
        for (j = i; j < 3; j++) {
            a3[j] = '\0';
        }

        fromA3ToA4(a4, a3);

        for (j = 0; j < i + 1; j++) {
            output[encLen++] = b64_alphabet[a4[j]];
        }

        while ((i++ < 3)) {
            output[encLen++] = '=';
        }
    }
    output[encLen] = '\0';
    return encLen;
}

int decode(unsigned char *output, char *input, int inputLength) {
    int i = 0, j = 0;
    int decodedLength = 0;
    unsigned char A3[3];
    unsigned char A4[4];


    while (inputLength--) {
        if (*input == '=') {
            break;
        }

        A4[i++] = *(input++);
        if (i == 4) {
            for (i = 0; i < 4; i++) {
                A4[i] = lookupTable(A4[i]);
            }

            fromA4ToA3(A3, A4);

            for (i = 0; i < 3; i++) {
                output[decodedLength++] = A3[i];
            }
            i = 0;
        }
    }

    if (i) {
        for (j = i; j < 4; j++) {
            A4[j] = '\0';
        }

        for (j = 0; j < 4; j++) {
            A4[j] = lookupTable(A4[j]);
        }

        fromA4ToA3(A3, A4);

        for (j = 0; j < i - 1; j++) {
            output[decodedLength++] = A3[j];
        }
    }
    output[decodedLength] = '\0';
    return decodedLength;
}

char *encodeData(char *data) {
    int index = strchr(data, '=') - data;
    if (index > 0) {
        int len = ((index + 3) / 4) * 4;
        data = realloc(data, len + 1);
        memset(data + len, '=', len - index);
        data[len] = '\0';
    }
    int size = strlen(data);
    // int outSize;
    unsigned char *bytes = (unsigned char *) malloc(size);
    int outSize = encode(bytes, data, size);
    if (outSize == 0) {
        printf("Error decoding base64 data. Error code: %d\n", outSize);
        return NULL;
    }
    char *utf8String = (char *) malloc(outSize + 1);
    memcpy(utf8String, bytes, outSize);
    utf8String[outSize] = '\0';
    free(bytes);
    return utf8String;
}

char *decodeData(char *data) {
    int index = strchr(data, '=') - data;
    if (index > 0) {
        int len = ((index + 3) / 4) * 4;
        data = realloc(data, len + 1);
        memset(data + len, '=', len - index);
        data[len] = '\0';
    }
    int size = strlen(data);
    // int outSize;
    unsigned char *bytes = (unsigned char *) malloc(size);
    int outSize = decode(bytes, data, size);
    if (outSize == 0) {
        printf("Error decoding base64 data. Error code: %d\n", outSize);
        return NULL;
    }
    char *utf8String = (char *) malloc(outSize + 1);
    memcpy(utf8String, bytes, outSize);
    utf8String[outSize] = '\0';
    free(bytes);
    return utf8String;
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_encode64(JNIEnv *env, jobject thiz, jstring b) {
    Jenv = env;
    const char *inCStr2 = (*env)->GetStringUTFChars(env, b, NULL);
    JthisClass = (*env)->GetObjectClass(env, thiz);
// __android_log_print(ANDROID_LOG_DEBUG, "Libs|encodeVal", "%s", inCStr2);
// __android_log_print(ANDROID_LOG_DEBUG, "Libs|encode ", "%s", "SUCCESS");
    return (*env)->NewStringUTF(env, encodeData(inCStr2));
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevBogor(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevBogor(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBogor(JNIEnv *env, jobject thiz) {
    //return (*env)->NewStringUTF(env, "https://buskita-api.karcisku.id/c_bus/");
    return (*env)->NewStringUTF(env, "https://kabbogor.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlUpLog(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://log-service.net2software.net/api/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdBogor(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://kabbogor.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
    //return (*env)->NewStringUTF(env, "https://transpatriot.net2software.net/c_bus");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
    //return (*env)->NewStringUTF(env, "https://transpatriot.net2software.net/c_bus");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transpatriot.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBekasiUngu(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transbeken.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBekasiUnguDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transbeken.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBalikPapan(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://sinarjayagrup.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transpatriot.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevDepok(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevDepok(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdDepok(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transdepok.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdDepok(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transdepok.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevCitraMaja(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevCitraMaja(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdCitraMaja(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://buscitra.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdCitraMaja(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://buscitra.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevCitraRaya(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://afc-citraraya.net2software.net/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevCitraRaya(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://afc-citraraya.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdCitraRaya(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://buscitrarayatgr.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdCitraRaya(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://buscitrarayatgr.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevKabupatenBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevKabupatenBekasiDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdKabupatenBekasi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://wibawamukti.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdKabupatenBekasiDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://wibawamukti.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevTangerang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevTangerang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdTangerang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://btskotatangerang.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdTangerang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://btskotatangerang.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevAngkot(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdAngkot(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://anglisdepok.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevBagong(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://api-z90.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBagong(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://api-z90.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevKodjari(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevKodjariDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdKodjari(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transpakuan.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdKodjariDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transpakuan.karcisku.id");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevSby(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-suroboyo.net2software.net/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevSby(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-suroboyo.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdSby(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://suroboyo-bus.jaring.host/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdSby(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://suroboyo-bus.jaring.host/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlPocAspiDirect(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://poc-tj.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlPocAspi(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://poc-tj.net2software.net/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getEncryptionKey(JNIEnv *env, jobject thiz) {
    // return (*env)->NewStringUTF(env, "clRUNGJlek9JSXVrbjdBNC9sUFhlME9yR0ViU29JMkY=");
    return (*env)->NewStringUTF(env, "JARINGSOLUSIAPLI");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getEncryptionQRKey(JNIEnv *env, jobject thiz) {
    // return (*env)->NewStringUTF(env, "clRUNGJlek9JSXVrbjdBNC9sUFhlME9yR0ViU29JMkY=");
    return (*env)->NewStringUTF(env, "DAMRIJARINGSOLUSIAPLIKASI");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getEncryptionIV(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "UW00ZHNPVUtFZnAxcG5HdA==");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getEncryptionSecret(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "123");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getUrlMqttProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "mqtt.jsa2.host");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getPortMqttProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "12345");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getUrlMqttDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "192.168.66.201");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getPortMqttDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "1883");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevDemo(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdDemo(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://transdepok.karcisku.id/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevDamri(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevDamri(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevDamriHTTPS(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdDamri(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdDamri(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdDamriHTTPS(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://ppd.jsa2.host");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisCpmDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisCpmProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisBRIDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisBRIProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDamriQrisCpmDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDamriQrisCpmProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host/qriscpm/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisMultiDev(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qrismultitap/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlQrisMultiProd(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "https://dev-buskita.karcisku.id/qrismultitap/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlDevBontang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net/c_bus");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlDevBontang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://dev-damri.net2software.net");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getBaseUrlProdBontang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host/c_bus/");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdBontang(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host");
}

JNIEXPORT jstring JNICALL
Java_com_net2software_encengine_EncEngine_getDirectUrlProdBalikPapan(JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "http://ppd.jsa2.host");
}
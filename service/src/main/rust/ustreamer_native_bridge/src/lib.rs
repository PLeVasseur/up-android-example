use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant};
use async_std::task;
use jni::objects::{JByteArray, JClass, JObject, JString, JValue, ReleaseMode};
use jni::sys::{jbyteArray, jlong, jobject, jstring};
use jni::JNIEnv;
use log::{error, info, LevelFilter, trace};
use up_client_android::UPClientAndroid;
use up_rust::{UAuthority, UCode, UEntity, UListener, UMessage, UMessageBuilder, UResource, UStatus, UTransport, UUIDBuilder, UUri};
use up_streamer::UStreamer;
use android_logger::Config;
use async_trait::async_trait;
use bytes::Bytes;
use protobuf::Message;
use up_rust::UPayloadFormat::UPAYLOAD_FORMAT_RAW;

const CLASS_UMESSAGE: &str = "Lorg/eclipse/uprotocol/v1/UMessage;";

#[derive(Clone)]
pub struct DummyListener;

#[async_trait]
impl UListener for DummyListener {
    async fn on_receive(&self, msg: UMessage) {
        trace!("Pinged from inside of DummyListener: message: {msg:?}");
    }

    async fn on_error(&self, err: UStatus) {
        todo!()
    }
}

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_org_eclipse_uprotocol_streamer_service_NativeBridge_initializeStreamer<
    'local,
>(
    mut env: JNIEnv<'local>,
    // This is the class that owns our static method. It's not going to be used,
    // but still must be present to match the expected signature of a static
    // native method.
    class: JClass<'local>,
    up_client: JObject,
    usub: JObject,
    uuri_class: JClass,
    ustatus_class: JClass,
    ulistener_native_bridge_class: JClass,
    native_bridge_class: JClass,
    class_loader: JObject
) -> jstring {

    android_logger::init_once(
        Config::default().with_max_level(LevelFilter::Trace),
    );

    log::info!("entered initializeStreamer");

    // Convert local references to global references
    let up_client = env
        .new_global_ref(up_client)
        .expect("Failed to create global ref for up_client");
    let usub = env
        .new_global_ref(usub)
        .expect("Failed to create global ref for usub");

    let uuri_class = env.new_global_ref(uuri_class).expect("Failed to create global ref for uuri_class");
    let ustatus_class = env.new_global_ref(ustatus_class).expect("Failed to create global ref for ustatus_class");
    let ulistener_native_bridge_class = env.new_global_ref(ulistener_native_bridge_class).expect("Failed to create global ref for ulistener_native_bridge_class");
    let native_bridge_class = env.new_global_ref(native_bridge_class).expect("Failed to create global ref for native_bridge_class");

    // Obtain the JavaVM from the JNIEnv
    let vm = env.get_java_vm().expect("Failed to get JavaVM");

    let class_loader_global_ref = env.new_global_ref(class_loader);

    thread::spawn(move ||{

        let before = Instant::now();

        let binding = class_loader_global_ref.unwrap();
        let class_loader = binding.as_obj();

        // Get JNIEnv for the current thread
        let mut env =
            vm
            .attach_current_thread()
            .expect("Failed to attach current thread");
        let the_class_name = env.new_string("org/eclipse/uprotocol/streamer/service/UListenerNativeBridge");
        let Ok(the_class_name) = the_class_name else {
            error!("Failed to set the_class_name");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        let after_attaching_jni_env = Instant::now();

        // Load the class with that name using that class loader.
        let the_class = env.call_method(
            &class_loader,
            "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;",
            &[
                (&the_class_name).into(),
            ],
        );
        let Ok(the_class) = the_class else {
            error!("Failed to load the class");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        let after_loading_class = Instant::now();

        info!("Was able to load the class!");

        let Ok(the_class) = the_class.l() else {
            error!("Unable to convert to JObject");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        info!("Was able to turn JValueOwned to JObject!");

        // seems maybe we're able to save the class here...?
        let the_class = JClass::from(the_class);

        let after_converting_to_jclass = Instant::now();

        let dur_attaching_jni_env = (after_attaching_jni_env - before).as_nanos();
        info!("attaching JNIEnv to thread cost in ns: {}", dur_attaching_jni_env);

        let dur_loading_class = (after_loading_class - after_attaching_jni_env).as_nanos();
        info!("loading class cost in ns: {}", dur_loading_class);

        let dur_converting_jobject_to_jclass = (after_converting_to_jclass - after_loading_class).as_nanos();
        info!("converting jobject to jclass cost in ns: {}", dur_converting_jobject_to_jclass);

        let vm = env.get_java_vm().expect("Failed to get JavaVM");

        for i in 0..2 {
            let after = Instant::now();

            let id: u64 = 123;

            let before_constructing_class = Instant::now();
            let the_class_ref: &JClass = the_class.as_ref();
            let Ok(the_object) =
                env.new_object(the_class_ref, "(J)V", &[JValue::Long(id as jlong)])
                else {
                    error!("Failed to create a new instance of UListenerNativeBridge class");
                    env.exception_describe().unwrap();
                    env.exception_clear().unwrap();
                    return;
                };
            let after_constructing_class = Instant::now();
            let dur_constructing_object = (after_constructing_class - before_constructing_class).as_nanos();

            info!("Was able to construct the object on time {i}");
            info!("constructing object took in ns: {}", dur_constructing_object);
        }

        let before_loading_native_bridge = Instant::now();

        let the_class_name = env.new_string("org/eclipse/uprotocol/streamer/service/NativeBridge");
        let Ok(the_class_name) = the_class_name else {
            error!("Failed to set the_class_name");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        // Load the class with that name using that class loader.
        let the_class = env.call_method(
            &class_loader,
            "loadClass",
            "(Ljava/lang/String;)Ljava/lang/Class;",
            &[
                (&the_class_name).into(),
            ],
        );
        let Ok(the_class) = the_class else {
            error!("Failed to load the class");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        let after_loading_native_bridge = Instant::now();

        info!("Was able to load the NativeBridge class!");

        let Ok(the_class) = the_class.l() else {
            error!("Unable to convert to JObject");
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        info!("Was able to turn JValueOwned to JObject!");

        // seems maybe we're able to save the class here...?
        let the_class = JClass::from(the_class);

        let after_converting_to_jclass = Instant::now();

        let my_cool_topic = UUri {
            entity: Some(UEntity {
                name: "client.rust".to_string(),
                id: Some(1),
                version_major: Some(1),
                ..Default::default()
            }).into(),
            resource: Some(UResource {
                name: "VeryCoolResource".to_string(),
                id: Some(1),
                ..Default::default()
            }).into(),
            ..Default::default()
        };
        let mut msg_builder = UMessageBuilder::publish(my_cool_topic);
        let my_cool_vec: Vec<u8> = vec![1, 2, 3, 4];
        let msg = msg_builder.with_message_id(UUIDBuilder::build()).build_with_payload(Bytes::from(my_cool_vec), UPAYLOAD_FORMAT_RAW).expect("failed to build message");

        let before_serialize_to_bytes_deserialize_to_java_umessage = Instant::now();

        let msg_bytes = msg.write_to_bytes().expect("unable to serialize message to bytes");
        let byte_array = env
            .byte_array_from_slice(&msg_bytes)
            .expect("Couldn't create jbyteArray from Rust Vec<u8>");
        trace!(
            "Turned byte vec into JByteArray",
        );
        let jvalue_byte_array = JValue::Object(&*byte_array);

        let the_class_ref: &JClass = the_class.as_ref();

        let Ok(umessage_object) = env.call_static_method(
            the_class_ref,
            "deserializeToUMessage",
            format!("([B){CLASS_UMESSAGE}"),
            &[jvalue_byte_array],
        ) else {
            trace!(
                "Failed when calling deserializeToUMessage",
            );
            env.exception_describe().unwrap();
            env.exception_clear().unwrap();
            return;
        };

        let after_serialize_to_bytes_deserialize_to_java_umessage = Instant::now();
        let dur_serialize_to_bytes_deserialize_to_java_umessage = (after_serialize_to_bytes_deserialize_to_java_umessage - before_serialize_to_bytes_deserialize_to_java_umessage).as_nanos();

        info!("succeeded when calling deserializeToUMessage, took in ns: {}", dur_serialize_to_bytes_deserialize_to_java_umessage);
    });

    // // task::spawn(async move{
    //
    //     let ustreamer = UStreamer::new("AndroidStreamer", 100);
    //     let up_client_android = task::block_on(UPClientAndroid::new(vm, up_client, usub, uuri_class, ustatus_class, ulistener_native_bridge_class, native_bridge_class));
    //
    //     let dummy_uuri = UUri {
    //         entity: Some(UEntity {
    //             name: "client.test".to_string(),
    //             version_major: Some(1),
    //             ..Default::default()
    //         }).into(),
    //         resource: Some(UResource {
    //             name: "resource".to_string(),
    //             instance: Some("main".to_string()),
    //             message: Some("Rust".to_string()),
    //             ..Default::default()
    //         }).into(),
    //         ..Default::default()
    //     };
    //
    //     let dummy_listener = Arc::new(DummyListener);
    //     let register_res = task::block_on(up_client_android.register_listener(dummy_uuri, dummy_listener));
    //     info!("Registration result: {register_res:?}");
    //
    // // });
    //
    // // let up_client_zenoh = ...;
    //
    // info!("sleeping here to let this this process, would need to do something more effective like have this be event driven");
    //
    // thread::sleep(Duration::from_millis(20000));
    //
    // info!("after sleeping");

    let empty_string = "";
    let mock_string = "mock_string";
    let status_strings = vec![empty_string, mock_string];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env
        .new_string(status_string)
        .expect("Couldn't create java string!");

    log::info!("exiting initializeStreamer");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_org_eclipse_uprotocol_streamer_service_NativeBridge_teardownStreamer<
    'local,
>(
    mut env: JNIEnv<'local>,
    // This is the class that owns our static method. It's not going to be used,
    // but still must be present to match the expected signature of a static
    // native method.
    class: JClass<'local>,
) -> jstring {


    let empty_string = "";
    let status_strings = vec![empty_string];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env
        .new_string(status_string)
        .expect("Couldn't create java string!");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}


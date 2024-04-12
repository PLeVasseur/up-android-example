use std::sync::Arc;
use std::thread;
use std::time::Duration;
use async_std::task;
use jni::objects::{JByteArray, JClass, JObject, ReleaseMode};
use jni::sys::{jbyteArray, jobject, jstring};
use jni::JNIEnv;
use log::{info, LevelFilter, trace};
use up_client_android::UPClientAndroid;
use up_rust::{UAuthority, UEntity, UListener, UMessage, UResource, UStatus, UTransport, UUri};
use up_streamer::UStreamer;
use android_logger::Config;
use async_trait::async_trait;


use protobuf::Message;

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
    native_bridge_class: JClass
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

    // thread::spawn(move ||{

        let ustreamer = UStreamer::new("AndroidStreamer", 100);
        let up_client_android = task::block_on(UPClientAndroid::new(vm, up_client, usub, uuri_class, ustatus_class, ulistener_native_bridge_class, native_bridge_class));

        let dummy_uuri = UUri {
            entity: Some(UEntity {
                name: "client.test".to_string(),
                version_major: Some(1),
                ..Default::default()
            }).into(),
            resource: Some(UResource {
                name: "resource".to_string(),
                instance: Some("main".to_string()),
                message: Some("Rust".to_string()),
                ..Default::default()
            }).into(),
            ..Default::default()
        };

        let dummy_listener = Arc::new(DummyListener);
        let register_res = task::block_on(up_client_android.register_listener(dummy_uuri, dummy_listener));
        info!("Registration result: {register_res:?}");
    // });

    // let up_client_zenoh = ...;

    info!("sleeping here to let this this process, would need to do something more effective like have this be event driven");

    thread::sleep(Duration::from_millis(20000));

    info!("after sleeping");

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


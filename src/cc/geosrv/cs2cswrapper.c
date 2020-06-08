#include <stdlib.h>
#include <proj.h>
#include "cs2cswrapper.h"


typedef struct
{
	PJ* P;
	PJ_CONTEXT* C;
}
ProjCtx;


JNIEXPORT jlong JNICALL Java_cc_geosrv_Proj_init(JNIEnv* env, jobject thisObj, jstring sFromCs, jstring sToCs)
{
	PJ_CONTEXT* C = proj_context_create();
	if (C == NULL) // multi-threaded context creation failed
		return 0;

	const char* szFromCs = (*env)->GetStringUTFChars(env, sFromCs, NULL);
	const char* szToCs = (*env)->GetStringUTFChars(env, sToCs, NULL);

	PJ* P = proj_create_crs_to_crs(C, szFromCs, szToCs, NULL); // create initial projection

	(*env)->ReleaseStringUTFChars(env, sToCs, szToCs); // release string copies before continuing
	(*env)->ReleaseStringUTFChars(env, sFromCs, szFromCs);

	if (P == NULL) // initial projection creation failed
	{
		proj_context_destroy(C); // release context
		return 0;
	}

	PJ* Pn = proj_normalize_for_visualization(C, P); // create normalized projection
	proj_destroy(P); // release initial projection

	if (Pn == NULL) // normalized projection creation failed
	{
		proj_context_destroy(C); // release context
		return 0;
	}

	ProjCtx* pProjCtx = (ProjCtx*)malloc(sizeof(ProjCtx));
	pProjCtx->P = Pn; // save normalized projection
	pProjCtx->C = C; // and its context
	return (jlong)pProjCtx;
}


JNIEXPORT void JNICALL Java_cc_geosrv_Proj_proj(JNIEnv* env, jobject thisObj, jlong lJniProj, jdouble dX, jdouble dY, jdoubleArray dPoint)
{
	PJ_COORD oFrom = proj_coord(dX, dY, 0.0, 0.0); // normalized projection context forces
	PJ_COORD oTo = proj_trans(((ProjCtx*)lJniProj)->P, PJ_FWD, oFrom); // horizontal then vertical coordinate order

	jdouble* dPt = (*env)->GetDoubleArrayElements(env, dPoint, NULL);
	dPt[0] = oTo.lp.lam;
	dPt[1] = oTo.lp.phi;

	(*env)->ReleaseDoubleArrayElements(env, dPoint, dPt, 0); // copy back and release
}


JNIEXPORT void JNICALL Java_cc_geosrv_Proj_free(JNIEnv* env, jobject thisObj, jlong lJniProj)
{
	ProjCtx* pProjCtx = (ProjCtx*)lJniProj; // null pointer guarded externally
	proj_destroy(pProjCtx->P); // release projection
	pProjCtx->P = NULL;
	proj_context_destroy(pProjCtx->C); // release context
	pProjCtx->C = NULL;
	free(pProjCtx); // finally free struct
}
